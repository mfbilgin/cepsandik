package com.cepsandik.electionservice.service;

import com.cepsandik.electionservice.client.CryptoEngineClient;
import com.cepsandik.electionservice.dto.request.CastVoteRequest;
import com.cepsandik.electionservice.dto.response.*;
import com.cepsandik.electionservice.entity.*;
import com.cepsandik.electionservice.exception.ApiException;
import com.cepsandik.electionservice.grpc.EncryptBallotResponse;
import com.cepsandik.electionservice.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class VoteService {

    private final ElectionRepository electionRepository;
    private final CandidateRepository candidateRepository;
    private final AccessCodeRepository accessCodeRepository;
    private final VoteTokenRepository voteTokenRepository;
    private final VoteRepository voteRepository;
    private final CryptoEngineClient cryptoEngineClient;

    // ==================== Erişim Kodu Doğrulama ====================

    /**
     * Erişim kodunu doğrular ve seçim bilgilerini döndürür.
     * Kod geçerliyse kullanım sayısını artırır.
     */
    @Transactional
    public AccessVerificationResponse verifyAccessCode(Long electionId, String code) {
        Election election = findElectionOrThrow(electionId);

        // Seçim aktif mi kontrol et
        if (!election.isActive()) {
            throw ApiException.badRequest("Bu seçim şu an aktif değil");
        }

        // Erişim kodunu bul ve doğrula
        AccessCode accessCode = accessCodeRepository.findByCode(code.toUpperCase())
                .orElseThrow(() -> ApiException.notFound("Geçersiz erişim kodu"));

        // Kodun bu seçime ait olup olmadığını kontrol et
        if (!accessCode.getElection().getId().equals(electionId)) {
            throw ApiException.badRequest("Bu erişim kodu bu seçime ait değil");
        }

        // Kodun geçerliliğini kontrol et
        if (!accessCode.isValid()) {
            if (!accessCode.getIsActive()) {
                throw ApiException.badRequest("Bu erişim kodu deaktif edilmiş");
            }
            if (accessCode.getExpiresAt() != null && accessCode.getExpiresAt().isBefore(java.time.LocalDateTime.now())) {
                throw ApiException.badRequest("Bu erişim kodunun süresi dolmuş");
            }
            if (accessCode.getMaxUses() != null && accessCode.getCurrentUses() >= accessCode.getMaxUses()) {
                throw ApiException.badRequest("Bu erişim kodu maksimum kullanım sayısına ulaşmış");
            }
        }

        // Kullanım sayısını artır
        accessCode.incrementUsage();
        accessCodeRepository.save(accessCode);

        // Adayları getir
        List<Candidate> candidates = candidateRepository.findByElectionIdAndIsDeletedFalseOrderByDisplayOrderAsc(electionId);
        List<CandidateResponse> candidateResponses = candidates.stream()
                .map(c -> CandidateResponse.builder()
                        .id(c.getId())
                        .name(c.getName())
                        .description(c.getDescription())
                        .imageUrl(c.getImageUrl())
                        .displayOrder(c.getDisplayOrder())
                        .createdAt(c.getCreatedAt())
                        .build())
                .collect(Collectors.toList());

        return AccessVerificationResponse.builder()
                .electionId(election.getId())
                .electionTitle(election.getTitle())
                .electionDescription(election.getDescription())
                .status(election.getStatus())
                .type(election.getType())
                .maxSelections(election.getMaxSelections())
                .startTime(election.getStartTime())
                .endTime(election.getEndTime())
                .candidateCount(candidateResponses.size())
                .candidates(candidateResponses)
                .anonymousVoting(election.getAnonymousVoting())
                .build();
    }

    // ==================== Vote Token Yönetimi ====================

    /**
     * Kullanıcı için tek kullanımlık vote token üretir.
     * Her kullanıcı bir seçimde sadece 1 token alabilir.
     */
    @Transactional
    public VoteTokenResponse generateVoteToken(Long electionId, String userId) {
        Election election = findElectionOrThrow(electionId);

        // Seçim aktif mi kontrol et
        if (!election.isActive()) {
            throw ApiException.badRequest("Bu seçim şu an aktif değil, oy token'ı alınamaz");
        }

        // Kullanıcı zaten token almış mı kontrol et
        var existingToken = voteTokenRepository.findByElectionIdAndUserId(electionId, userId);
        if (existingToken.isPresent()) {
            VoteToken token = existingToken.get();
            return VoteTokenResponse.builder()
                    .token(token.getToken())
                    .electionId(election.getId())
                    .electionTitle(election.getTitle())
                    .isUsed(token.getIsUsed())
                    .createdAt(token.getCreatedAt())
                    .usedAt(token.getUsedAt())
                    .build();
        }

        // Yeni token üret
        VoteToken voteToken = VoteToken.builder()
                .election(election)
                .userId(userId)
                .token(UUID.randomUUID().toString())
                .build();

        voteToken = voteTokenRepository.save(voteToken);

        log.info("Vote token üretildi - Election: {}, User: {}", electionId, userId);

        return VoteTokenResponse.builder()
                .token(voteToken.getToken())
                .electionId(election.getId())
                .electionTitle(election.getTitle())
                .isUsed(false)
                .createdAt(voteToken.getCreatedAt())
                .build();
    }

    // ==================== Oy Verme ====================

    /**
     * Oy kullanır. İdempotent: aynı token ile tekrar oy atılırsa mevcut oy döndürülür.
     */
    @Transactional
    public VoteResponse castVote(Long electionId, CastVoteRequest request) {
        Election election = findElectionOrThrow(electionId);

        // Seçim aktif mi kontrol et
        if (!election.isActive()) {
            throw ApiException.badRequest("Bu seçim şu an aktif değil, oy kullanılamaz");
        }

        // Vote token'ı doğrula
        VoteToken voteToken = voteTokenRepository.findByToken(request.getVoteToken())
                .orElseThrow(() -> ApiException.notFound("Geçersiz oy token'ı"));

        // Token bu seçime ait mi
        if (!voteToken.getElection().getId().equals(electionId)) {
            throw ApiException.badRequest("Bu token bu seçime ait değil");
        }

        // İdempotency: token zaten kullanılmışsa mevcut oyu döndür
        if (voteToken.getIsUsed()) {
            Vote existingVote = voteRepository.findByVoteToken(request.getVoteToken())
                    .orElseThrow(() -> new ApiException(HttpStatus.INTERNAL_SERVER_ERROR,
                            "Token kullanılmış ama oy bulunamadı"));

            log.info("İdempotent oy isteği - Election: {}, Token: {}", electionId, request.getVoteToken());

            return VoteResponse.builder()
                    .electionId(election.getId())
                    .electionTitle(election.getTitle())
                    .candidateId(existingVote.getCandidate().getId())
                    .candidateName(existingVote.getCandidate().getName())
                    .alreadyVoted(true)
                    .votedAt(existingVote.getCreatedAt())
                    .build();
        }

        // Adayın geçerli olup olmadığını kontrol et
        Candidate candidate = candidateRepository.findById(request.getCandidateId())
                .orElseThrow(() -> ApiException.notFound("Aday bulunamadı"));

        if (!candidate.getElection().getId().equals(electionId)) {
            throw ApiException.badRequest("Bu aday bu seçime ait değil");
        }

        if (candidate.getIsDeleted()) {
            throw ApiException.badRequest("Bu aday silinmiş");
        }

        // Oyu kaydet
        Vote vote = Vote.builder()
                .election(election)
                .candidate(candidate)
                .voteToken(request.getVoteToken())
                .build();

        // === Crypto-Engine: ElGamal şifreleme ===
        if (election.getElectionGuardContext() != null && request.getRsaEncryptedPayload() != null) {
            try {
                EncryptBallotResponse cryptoResponse = cryptoEngineClient.encryptBallot(
                        String.valueOf(electionId),
                        election.getElectionGuardContext(),
                        election.getElectionManifest(),
                        request.getRsaEncryptedPayload(),
                        request.getVoteToken(),
                        "ballot-style-1"
                );

                vote.setEncryptedBallot(cryptoResponse.getCiphertextBallot());
                vote.setTrackingCode(cryptoResponse.getTrackingCode());
                vote.setZkpProof(cryptoResponse.getZkpProof());

                log.info("Oy kriptografik olarak şifrelendi: election={}, tracking_code={}",
                        electionId, cryptoResponse.getTrackingCode().substring(0, Math.min(16, cryptoResponse.getTrackingCode().length())));

            } catch (Exception e) {
                log.error("Crypto-Engine EncryptBallot hatası: election={}, ballot={}",
                        electionId, request.getVoteToken(), e);
                // Kriptografik şifreleme başarısız olursa oyu yine kaydet
                // ancak şifreli veri olmadan
                log.warn("Şifreli oy kaydedilemedi, düz oy olarak devam ediliyor.");
            }
        }

        vote = voteRepository.save(vote);

        // Token'ı kullanıldı olarak işaretle
        voteToken.markAsUsed();
        voteTokenRepository.save(voteToken);

        log.info("Oy kullanıldı - Election: {}, Candidate: {}", electionId, request.getCandidateId());

        return VoteResponse.builder()
                .electionId(election.getId())
                .electionTitle(election.getTitle())
                .candidateId(candidate.getId())
                .candidateName(candidate.getName())
                .alreadyVoted(false)
                .votedAt(vote.getCreatedAt())
                .build();
    }

    // ==================== Oy Durumu ====================

    /**
     * Kullanıcının belirli bir seçimdeki oy durumunu kontrol eder.
     */
    @Transactional(readOnly = true)
    public VoteResponse getMyVoteStatus(Long electionId, String userId) {
        Election election = findElectionOrThrow(electionId);

        var tokenOptional = voteTokenRepository.findByElectionIdAndUserId(electionId, userId);

        if (tokenOptional.isEmpty()) {
            return VoteResponse.builder()
                    .electionId(election.getId())
                    .electionTitle(election.getTitle())
                    .alreadyVoted(false)
                    .build();
        }

        VoteToken voteToken = tokenOptional.get();

        if (!voteToken.getIsUsed()) {
            return VoteResponse.builder()
                    .electionId(election.getId())
                    .electionTitle(election.getTitle())
                    .alreadyVoted(false)
                    .build();
        }

        // Anonim seçimde aday bilgisini gösterme
        if (election.getAnonymousVoting()) {
            return VoteResponse.builder()
                    .electionId(election.getId())
                    .electionTitle(election.getTitle())
                    .alreadyVoted(true)
                    .votedAt(voteToken.getUsedAt())
                    .build();
        }

        // Anonim olmayan seçimde, oy detayını göster
        var voteOptional = voteRepository.findByVoteToken(voteToken.getToken());
        if (voteOptional.isPresent()) {
            Vote vote = voteOptional.get();
            return VoteResponse.builder()
                    .electionId(election.getId())
                    .electionTitle(election.getTitle())
                    .candidateId(vote.getCandidate().getId())
                    .candidateName(vote.getCandidate().getName())
                    .alreadyVoted(true)
                    .votedAt(vote.getCreatedAt())
                    .build();
        }

        return VoteResponse.builder()
                .electionId(election.getId())
                .electionTitle(election.getTitle())
                .alreadyVoted(true)
                .votedAt(voteToken.getUsedAt())
                .build();
    }

    // ==================== Seçim İstatistikleri ====================

    /**
     * Seçim için oy istatistiklerini döndürür. Sadece CLOSED veya ARCHIVED seçimler için.
     */
    @Transactional(readOnly = true)
    public ElectionStatsResponse getElectionStats(Long electionId, String userId) {
        Election election = findElectionOrThrow(electionId);

        // Sonuçlar yayınlanmış mı veya kullanıcı seçim sahibi mi kontrol et
        boolean isOwner = election.getCreatedBy().equals(userId);
        boolean isClosed = election.isClosed() || election.getStatus().name().equals("ARCHIVED");

        if (!isOwner && !isClosed) {
            throw ApiException.forbidden("Seçim sonuçları henüz yayınlanmadı");
        }

        long totalVotes = voteRepository.countByElectionId(electionId);
        List<Object[]> voteCounts = voteRepository.countVotesByCandidateForElection(electionId);

        List<CandidateStatsResponse> candidateStats = voteCounts.stream()
                .map(row -> {
                    Long candidateId = (Long) row[0];
                    Long count = (Long) row[1];
                    Candidate candidate = candidateRepository.findById(candidateId).orElse(null);
                    return CandidateStatsResponse.builder()
                            .candidateId(candidateId)
                            .candidateName(candidate != null ? candidate.getName() : "Bilinmeyen")
                            .voteCount(count)
                            .percentage(totalVotes > 0 ? (double) count / totalVotes * 100 : 0)
                            .build();
                })
                .collect(Collectors.toList());

        return ElectionStatsResponse.builder()
                .electionId(election.getId())
                .electionTitle(election.getTitle())
                .status(election.getStatus())
                .totalVotes(totalVotes)
                .candidateStats(candidateStats)
                .build();
    }

    // ==================== ZKP / Verifiability Kanıtları ====================

    /**
     * Kullanıcının kendi oyuna ait kriptografik (ZKP) ve ElectionGuard kanıtlarını döndürür.
     */
    @Transactional(readOnly = true)
    public VoteProofResponse getMyVoteProof(Long electionId, String userId) {
        findElectionOrThrow(electionId);

        // Kullanıcının tokenını kontrol et
        VoteToken voteToken = voteTokenRepository.findByElectionIdAndUserId(electionId, userId)
                .orElseThrow(() -> ApiException.badRequest("Bu seçimde token sahibi değilsiniz"));

        if (!voteToken.getIsUsed()) {
            throw ApiException.badRequest("Henüz oy kullanmadınız");
        }

        // Oy tablosundan ZKP'yi çek
        Vote vote = voteRepository.findByVoteToken(voteToken.getToken())
                .orElseThrow(() -> ApiException.notFound("Oy kaydı bulunamadı (belki de şifreli kaydedilmedi)"));

        if (vote.getEncryptedBallot() == null || vote.getZkpProof() == null) {
            throw ApiException.badRequest("Bu oya ait ElectionGuard kriptografik kanıtları mevcut değil.");
        }

        return VoteProofResponse.builder()
                .electionId(electionId)
                .voteToken(vote.getVoteToken())
                .trackingCode(vote.getTrackingCode())
                .encryptedBallot(vote.getEncryptedBallot())
                .zkpProof(vote.getZkpProof())
                .build();
    }

    // ==================== Helper Methods ====================

    private Election findElectionOrThrow(Long id) {
        return electionRepository.findById(id)
                .filter(e -> !e.getIsDeleted())
                .orElseThrow(() -> ApiException.notFound("Seçim bulunamadı"));
    }
}
