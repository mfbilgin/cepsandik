package com.cepsandik.electionservice.service;

import com.cepsandik.electionservice.client.CryptoEngineClient;
import com.cepsandik.electionservice.dto.request.SubmitDecryptionShareRequest;
import com.cepsandik.electionservice.dto.request.SubmitGuardianKeyRequest;
import com.cepsandik.electionservice.entity.*;
import com.cepsandik.electionservice.enums.ElectionStatus;
import com.cepsandik.electionservice.exception.ApiException;
import com.cepsandik.electionservice.grpc.ContestInfo;
import com.cepsandik.electionservice.grpc.CreateJointKeyResponse;
import com.cepsandik.electionservice.grpc.GuardianPublicKey;
import com.cepsandik.electionservice.grpc.TallyElectionResponse;
import com.cepsandik.electionservice.grpc.DecryptionShare;
import com.cepsandik.electionservice.repository.CandidateRepository;
import com.cepsandik.electionservice.repository.ElectionGuardianRepository;
import com.cepsandik.electionservice.repository.ElectionRepository;
import com.cepsandik.electionservice.repository.VoteRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class GuardianService {

    private final ElectionRepository electionRepository;
    private final ElectionGuardianRepository guardianRepository;
    private final CandidateRepository candidateRepository;
    private final VoteRepository voteRepository;
    private final CryptoEngineClient cryptoEngineClient;
    private final BulletinOutboxService bulletinOutbox;

    public List<com.cepsandik.electionservice.dto.response.GuardianDutyResponse> getMyDuties(String userId) {
         return guardianRepository.findByUserId(UUID.fromString(userId)).stream()
                .map(g -> com.cepsandik.electionservice.dto.response.GuardianDutyResponse.builder()
                        .electionId(g.getElection().getId())
                        .electionTitle(g.getElection().getTitle())
                        .communityName(g.getElection().getCommunityId() != null ? String.valueOf(g.getElection().getCommunityId()) : null)
                        .status(g.getStatus().name())
                        .electionStatus(g.getElection().getStatus())
                        .encryptedTallyChallenge(buildEncryptedTallyChallenge(g.getElection().getId()))
                        .build())
                .toList();
    }

    /**
     * Emanetçinin kamu anahtarını ve taahhütlerini kaydeder.
     * Tüm emanetçiler yükleme yaptıysa Ortak Anahtar üretimini tetikler.
     */
    @Transactional
    public void submitPublicKey(Long electionId, String userId, SubmitGuardianKeyRequest request) {
        Election election = electionRepository.findById(electionId)
                .orElseThrow(() -> ApiException.notFound("Seçim bulunamadı"));

        ElectionGuardian guardian = guardianRepository.findByElectionIdAndUserId(electionId, UUID.fromString(userId))
                .orElseThrow(() -> ApiException.forbidden("Bu seçim için emanetçi değilsiniz"));

        if (!election.getStatus().equals(ElectionStatus.SCHEDULED)) {
            throw ApiException.badRequest("Sadece planlanmış seçimler için anahtar yüklenebilir");
        }

        guardian.setPublicKey(request.getPublicKey());
        guardian.setCommitments(request.getCommitments());
        guardian.setCoefficientProofs(request.getCoefficientProofs());
        guardian.setKeyBackups(request.getKeyBackups());
        guardian.setStatus(ElectionGuardian.GuardianStatus.KEY_UPLOADED);
        guardianRepository.save(guardian);

        log.info("Emanetçi anahtarı kaydedildi: electionId={}, userId={}", electionId, userId);

        // Herkes yükledi mi kontrol et
        long uploadedCount = guardianRepository.countByElectionIdAndStatus(electionId, ElectionGuardian.GuardianStatus.KEY_UPLOADED);
        List<ElectionGuardian> allGuardians = guardianRepository.findAllByElectionId(electionId);

        if (uploadedCount == allGuardians.size()) {
            log.info("Tüm emanetçiler anahtarlarını yükledi. Ortak anahtar üretiliyor...");
            generateJointKey(election, allGuardians);
        }
    }

    /**
     * Tüm bireysel anahtarları birleştirip seçimi ACTIVE durumuna getirir.
     */
    private void generateJointKey(Election election, List<ElectionGuardian> guardians) {
        try {
            List<Candidate> candidates = candidateRepository.findByElectionIdAndIsDeletedFalseOrderByDisplayOrderAsc(election.getId());
            
            // Protoleri oluştur
            List<GuardianPublicKey> gpKeys = guardians.stream()
                    .map(g -> GuardianPublicKey.newBuilder()
                            .setGuardianId(g.getUserId().toString())
                            .setPublicKey(g.getPublicKey())
                            .setCommitments(g.getCommitments())
                            .setCoefficientProofs(g.getCoefficientProofs() != null ? g.getCoefficientProofs() : "")
                            .build())
                    .collect(Collectors.toList());

            List<ContestInfo> contests = List.of(ContestInfo.newBuilder()
                    .setContestId("contest_" + election.getId())
                    .addAllSelectionIds(candidates.stream().map(c -> "candidate_" + c.getId()).toList())
                    .setNumberElected(election.getMaxSelections() != null ? election.getMaxSelections() : 1)
                    .setName(election.getTitle())
                    .build());

            CreateJointKeyResponse response = cryptoEngineClient.createJointKey(
                    election.getId().toString(),
                    guardians.size(),
                    election.getMinGuardiansThreshold(),
                    gpKeys,
                    contests,
                    election.getStartTime().toString(),
                    election.getEndTime().toString()
            );

            election.setElectionPublicKey(response.getJointPublicKey());
            election.setElectionGuardContext(response.getElectionGuardContext());
            election.setElectionManifest(response.getElectionManifest());
            election.setStatus(ElectionStatus.ACTIVE);
            
            electionRepository.save(election);
            bulletinOutbox.enqueue(
                    String.valueOf(election.getId()),
                    "ELECTION_SETUP",
                    null,
                    null,
                    response.getElectionManifest(),
                    true);
            log.info("Seçim {} aktif edildi ve ortak anahtar oluşturuldu.", election.getId());

        } catch (Exception e) {
            log.error("Ortak anahtar üretimi sırasında hata: ", e);
            throw new RuntimeException("Kriptografik seremoni başarısız oldu: " + e.getMessage());
        }
    }

    /**
     * Seçim sonunda deşifre payını kaydeder. 
     * Eşik değer (Q) aşılırsa sonuçları açıklar.
     */
    @Transactional
    public void submitDecryptionShare(Long electionId, String userId, SubmitDecryptionShareRequest request) {
        Election election = electionRepository.findById(electionId)
                .orElseThrow(() -> ApiException.notFound("Seçim bulunamadı"));

        ElectionGuardian guardian = guardianRepository.findByElectionIdAndUserId(electionId, UUID.fromString(userId))
                .orElseThrow(() -> ApiException.forbidden("Bu seçim için emanetçi değilsiniz"));

        // Seçim sonlanmış (CLOSED) olmalı ama henüz sonuçlar açıklanmamış olmalı (tally_proof null)
        if (election.getTallyProof() != null) {
            throw ApiException.badRequest("Bu seçimin sonuçları zaten açıklandı");
        }

        guardian.setDecryptionShare(request.getShareData());
        guardian.setStatus(ElectionGuardian.GuardianStatus.SHARE_UPLOADED);
        guardianRepository.save(guardian);

        // Q (Quorum) kadar pay toplandı mı?
        List<ElectionGuardian> managersWithShares = guardianRepository.findAllByElectionId(electionId).stream()
                .filter(g -> g.getDecryptionShare() != null)
                .toList();

        if (managersWithShares.size() >= election.getMinGuardiansThreshold()) {
            log.info("Yeterli deşifre payı toplandı ({} >= {}). Sonuçlar hesaplanıyor...", 
                    managersWithShares.size(), election.getMinGuardiansThreshold());
            finalizeTally(election, managersWithShares);
        }
    }

    private void finalizeTally(Election election, List<ElectionGuardian> shareHolders) {
        try {
            List<Vote> votes = voteRepository.findByElectionId(election.getId());
            List<String> ciphertextBallots = votes.stream()
                    .filter(v -> v.getEncryptedBallot() != null)
                    .map(Vote::getEncryptedBallot)
                    .toList();

            List<DecryptionShare> shares = shareHolders.stream()
                    .map(s -> DecryptionShare.newBuilder()
                            .setGuardianId(s.getUserId().toString())
                            .setShareData(s.getDecryptionShare())
                            .build())
                    .collect(Collectors.toList());

            TallyElectionResponse response = cryptoEngineClient.decryptWithShares(
                    election.getId().toString(),
                    election.getElectionGuardContext(),
                    election.getElectionManifest(),
                    ciphertextBallots,
                    shares
            );

            election.setTallyProof(response.getTallyProof());
            java.util.List<java.util.Map<String, Object>> contests = response.getResultsList().stream()
                    .map(contest -> java.util.Map.<String, Object>of(
                            "contest_id", contest.getContestId(),
                            "selections", contest.getSelectionsList().stream()
                                    .map(selection -> java.util.Map.<String, Object>of(
                                            "selection_id", selection.getSelectionId(),
                                            "tally", selection.getTally()))
                                    .toList()))
                    .toList();
            election.setTallyResults(new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(contests));
            electionRepository.save(election);
            bulletinOutbox.enqueue(
                    String.valueOf(election.getId()),
                    "TALLY_PUBLISHED",
                    null,
                    null,
                    election.getTallyResults(),
                    true);
            log.info("Seçim {} sonuçları başarıyla açıklandı.", election.getId());

        } catch (Exception e) {
            log.error("Tally birleştirme hatası: ", e);
            throw new RuntimeException("Deşifre payları birleştirilemedi: " + e.getMessage());
        }
    }
    private String buildEncryptedTallyChallenge(Long electionId) {
        List<String> ciphertextBallots = voteRepository.findByElectionId(electionId).stream()
                .filter(v -> v.getEncryptedBallot() != null)
                .map(Vote::getEncryptedBallot)
                .sorted()
                .toList();
        if (ciphertextBallots.isEmpty()) {
            return null;
        }
        return sha256Hex(String.join("|", ciphertextBallots));
    }

    private static String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 hesaplanamadi", e);
        }
    }
}
