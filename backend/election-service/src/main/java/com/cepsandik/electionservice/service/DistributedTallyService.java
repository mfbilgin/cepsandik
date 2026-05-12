package com.cepsandik.electionservice.service;

import com.cepsandik.electionservice.client.BulletinBoardClient;
import com.cepsandik.electionservice.client.CryptoEngineClient;
import com.cepsandik.electionservice.entity.Election;
import com.cepsandik.electionservice.entity.ElectionGuardian;
import com.cepsandik.electionservice.entity.Vote;
import com.cepsandik.electionservice.exception.ApiException;
import com.cepsandik.electionservice.grpc.GetChallengesResponse;
import com.cepsandik.electionservice.grpc.SessionAckResponse;
import com.cepsandik.electionservice.grpc.StartTallyDecryptionSessionResponse;
import com.cepsandik.electionservice.grpc.TallyElectionResponse;
import com.cepsandik.electionservice.repository.ElectionGuardianRepository;
import com.cepsandik.electionservice.repository.ElectionRepository;
import com.cepsandik.electionservice.repository.VoteRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Sprint 5.C.2.5 — Gerçek E2E-V threshold tally orchestrator (Java side).
 *
 * Election kapandığında {@link #startSessionForElection} çağrılır:
 *   1. KMP crypto-engine'in `StartTallyDecryptionSession` RPC'si tetiklenir
 *   2. Dönen sessionId + encrypted_tally_json Election entity'sine yazılır
 *   3. Mobile guardian'lar bu encrypted_tally üzerinden lokal KMP ile partial
 *      decryption + challenge response üretir, HTTP endpoint'leri üzerinden
 *      iletir
 *   4. Q (quorum) guardian CHALLENGE_RESPONSE_SUBMITTED durumuna gelince
 *      {@link #tryFinalize} otomatik tally'i açar (KMP DecryptorDoerre
 *      background thread'inde tamamlandığında server gelen finalize RPC
 *      cevabını döner).
 *
 * SUNUCU HİÇBİR guardian private key share'ini görmez — tüm crypto material
 * mobile cihazda kalır.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DistributedTallyService {

    private final ElectionRepository electionRepository;
    private final ElectionGuardianRepository guardianRepository;
    private final VoteRepository voteRepository;
    private final CryptoEngineClient cryptoEngineClient;
    private final BulletinBoardClient bulletinBoardClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    // ============ Server-driven (lifecycle hook) ============

    /**
     * Election ACTIVE → CLOSED geçişinde tetiklenir. KMP'de stateful tally
     * session başlatır; encrypted_tally_json ve sessionId'i kalıcılaştırır.
     */
    @Transactional
    public void startSessionForElection(Election election) {
        if (election.getTallySessionId() != null && !election.getTallySessionId().isBlank()) {
            log.info("Distributed tally session zaten başlatılmış: electionId={}, sessionId={}",
                    election.getId(), election.getTallySessionId());
            return;
        }
        if (election.getElectionGuardContext() == null || election.getElectionManifest() == null) {
            log.warn("Distributed session başlatılamadı (context/manifest yok): electionId={}", election.getId());
            return;
        }

        List<String> ciphertextBallots = voteRepository.findByElectionId(election.getId()).stream()
                .map(Vote::getEncryptedBallot)
                .filter(b -> b != null && !b.isBlank())
                .toList();
        if (ciphertextBallots.isEmpty()) {
            log.info("Oy yok, distributed session atlandı: electionId={}", election.getId());
            return;
        }

        // KEY_UPLOADED durumdaki guardian'lar bu tally'ye katılabilir.
        List<ElectionGuardian> eligible = guardianRepository.findAllByElectionId(election.getId()).stream()
                .filter(g -> g.getStatus() == ElectionGuardian.GuardianStatus.KEY_UPLOADED
                        || g.getStatus() == ElectionGuardian.GuardianStatus.SHARE_UPLOADED)
                .toList();
        if (eligible.size() < election.getMinGuardiansThreshold()) {
            log.warn("Yeterli guardian yok (eligible={}, quorum={}): electionId={}",
                    eligible.size(), election.getMinGuardiansThreshold(), election.getId());
            return;
        }
        List<String> participatingIds = eligible.stream().map(g -> g.getUserId().toString()).toList();

        StartTallyDecryptionSessionResponse response = cryptoEngineClient.startTallyDecryptionSession(
                String.valueOf(election.getId()),
                election.getElectionGuardContext(),
                election.getElectionManifest(),
                ciphertextBallots,
                participatingIds);

        election.setTallySessionId(response.getSessionId());
        election.setEncryptedTallyJson(response.getEncryptedTallyJson());
        electionRepository.save(election);

        log.info("Distributed tally session başlatıldı: electionId={}, sessionId={}, encryptedTallyJson={} bytes, participating={}",
                election.getId(), response.getSessionId(),
                response.getEncryptedTallyJson().length(), participatingIds.size());
    }

    // ============ Guardian-driven (HTTP endpoints) ============

    /**
     * Guardian (mobile) tally task'ını pull eder: sessionId + encrypted_tally
     * + manifest + jointPublicKey döner. Mobile lokal KMP ile partial
     * decryption hesaplar.
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getDecryptionTask(Long electionId, String userId) {
        Election election = electionRepository.findById(electionId)
                .orElseThrow(() -> ApiException.notFound("Seçim bulunamadı"));
        ElectionGuardian guardian = guardianRepository.findByElectionIdAndUserId(electionId, UUID.fromString(userId))
                .orElseThrow(() -> ApiException.forbidden("Bu seçim için emanetçi değilsiniz"));
        if (election.getTallySessionId() == null) {
            throw ApiException.badRequest("Tally session henüz başlatılmadı");
        }
        return Map.of(
                "sessionId", election.getTallySessionId(),
                "guardianId", guardian.getUserId().toString(),
                "encryptedTallyJson", election.getEncryptedTallyJson(),
                "electionGuardContext", election.getElectionGuardContext(),
                "electionManifest", election.getElectionManifest(),
                "jointPublicKey", election.getElectionPublicKey()
        );
    }

    /** Round 1 — mobile partial decryption sonucunu submit eder. */
    @Transactional
    public Map<String, Object> submitPartialDecryption(Long electionId, String userId, String partialsJson) {
        Election election = electionRepository.findById(electionId)
                .orElseThrow(() -> ApiException.notFound("Seçim bulunamadı"));
        ElectionGuardian guardian = guardianRepository.findByElectionIdAndUserId(electionId, UUID.fromString(userId))
                .orElseThrow(() -> ApiException.forbidden("Bu seçim için emanetçi değilsiniz"));
        if (election.getTallySessionId() == null) {
            throw ApiException.badRequest("Tally session başlatılmadı");
        }

        SessionAckResponse ack = cryptoEngineClient.submitPartialDecryption(
                election.getTallySessionId(), guardian.getUserId().toString(), partialsJson);

        if (ack.getAccepted()) {
            // Mevcut enum sınırlı — yeni durumu reuse ediyoruz; SHARE_UPLOADED
            // semantiğini "round 1 tamam" olarak yeniden yorumluyoruz.
            guardian.setStatus(ElectionGuardian.GuardianStatus.SHARE_UPLOADED);
            guardianRepository.save(guardian);
        }
        return Map.of("accepted", ack.getAccepted(), "state", ack.getState(), "error", ack.getError());
    }

    /** Round 2 — mobile challenges'ı pull eder (long-poll). */
    public Map<String, Object> getChallenges(Long electionId, String userId) {
        Election election = electionRepository.findById(electionId)
                .orElseThrow(() -> ApiException.notFound("Seçim bulunamadı"));
        ElectionGuardian guardian = guardianRepository.findByElectionIdAndUserId(electionId, UUID.fromString(userId))
                .orElseThrow(() -> ApiException.forbidden("Bu seçim için emanetçi değilsiniz"));
        if (election.getTallySessionId() == null) {
            throw ApiException.badRequest("Tally session başlatılmadı");
        }

        GetChallengesResponse response = cryptoEngineClient.getChallenges(
                election.getTallySessionId(), guardian.getUserId().toString());
        return Map.of("challengesJson", response.getChallengesJson());
    }

    /**
     * Round 3 — mobile challenge response submit eder. Quorum tamamlandıysa
     * (server tarafında DecryptorDoerre background thread'i bitince)
     * otomatik finalize'ı tetikler.
     */
    @Transactional
    public Map<String, Object> submitChallengeResponse(Long electionId, String userId, String responsesJson) {
        Election election = electionRepository.findById(electionId)
                .orElseThrow(() -> ApiException.notFound("Seçim bulunamadı"));
        ElectionGuardian guardian = guardianRepository.findByElectionIdAndUserId(electionId, UUID.fromString(userId))
                .orElseThrow(() -> ApiException.forbidden("Bu seçim için emanetçi değilsiniz"));
        if (election.getTallySessionId() == null) {
            throw ApiException.badRequest("Tally session başlatılmadı");
        }

        SessionAckResponse ack = cryptoEngineClient.submitChallengeResponse(
                election.getTallySessionId(), guardian.getUserId().toString(), responsesJson);

        if (ack.getAccepted()) {
            // SHARE_UPLOADED zaten Round 1 sonrası set edildi; Round 3 için
            // ayrı bir enum değeri eklemek gerekecek (Sprint 5.C.2.5 iyileştirme).
            // Şimdilik flag olarak coefficient_proofs alanını "DONE" yaparız.
            guardian.setCoefficientProofs("ROUND_3_DONE");
            guardianRepository.save(guardian);
        }

        // Tally finalize'i async kontrol et (tüm guardian'lar Round 3 tamamlamış olabilir).
        long doneCount = guardianRepository.findAllByElectionId(electionId).stream()
                .filter(g -> "ROUND_3_DONE".equals(g.getCoefficientProofs()))
                .count();
        boolean shouldFinalize = doneCount >= election.getMinGuardiansThreshold()
                && (election.getTallyProof() == null || election.getTallyProof().isBlank());

        Map<String, Object> result = new java.util.HashMap<>();
        result.put("accepted", ack.getAccepted());
        result.put("state", ack.getState());
        result.put("error", ack.getError());
        result.put("doneCount", doneCount);
        result.put("quorum", election.getMinGuardiansThreshold());
        result.put("finalized", false);

        if (shouldFinalize) {
            try {
                finalize(election);
                result.put("finalized", true);
            } catch (Exception e) {
                log.error("Distributed tally finalize FAIL: electionId={}", election.getId(), e);
                result.put("finalizeError", e.getMessage());
            }
        }
        return result;
    }

    /** Quorum sonrası tally finalize — KMP DecryptorDoerre tamamladı. */
    @Transactional
    public void finalize(Election election) {
        log.info("Distributed tally finalize başlıyor: electionId={}, sessionId={}",
                election.getId(), election.getTallySessionId());

        TallyElectionResponse response = cryptoEngineClient.finalizeTallyDecryption(election.getTallySessionId());
        election.setTallyProof(response.getTallyProof());

        List<Map<String, Object>> contests = response.getResultsList().stream()
                .map(contest -> Map.<String, Object>of(
                        "contest_id", contest.getContestId(),
                        "selections", contest.getSelectionsList().stream()
                                .map(sel -> Map.<String, Object>of(
                                        "selection_id", sel.getSelectionId(),
                                        "tally", sel.getTally()))
                                .collect(Collectors.toList())))
                .toList();
        try {
            election.setTallyResults(objectMapper.writeValueAsString(contests));
        } catch (Exception e) {
            throw new RuntimeException("Tally results JSON serialize hatası", e);
        }
        electionRepository.save(election);

        try {
            bulletinBoardClient.appendRecord(
                    String.valueOf(election.getId()),
                    "TALLY_PUBLISHED",
                    null, null,
                    election.getTallyResults());
        } catch (Exception bbEx) {
            log.warn("Bulletin board TALLY_PUBLISHED yazılamadı: electionId={}, hata={}",
                    election.getId(), bbEx.getMessage());
        }

        log.info("Distributed tally publish OK: electionId={}, contests={}",
                election.getId(), contests.size());
    }
}
