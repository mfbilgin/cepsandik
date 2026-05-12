package com.cepsandik.electionservice.service;

import com.cepsandik.electionservice.client.BulletinBoardClient;
import com.cepsandik.electionservice.client.CryptoEngineClient;
import com.cepsandik.electionservice.config.UtcClock;
import com.cepsandik.electionservice.dto.request.CastVoteRequest;
import com.cepsandik.electionservice.dto.response.*;
import com.cepsandik.electionservice.entity.*;
import com.cepsandik.electionservice.exception.ApiException;
import com.cepsandik.electionservice.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
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
    private final VoteNullifierRepository voteNullifierRepository;
    private final BulletinBoardClient bulletinBoardClient;
    private final CryptoEngineClient cryptoEngineClient;
    private final UtcClock utcClock;

    @Transactional
    public AccessVerificationResponse verifyAccessCode(Long electionId, String code) {
        Election election = findElectionOrThrow(electionId);
        if (!election.isActive()) {
            throw ApiException.badRequest("Bu secim su an aktif degil");
        }

        AccessCode accessCode = accessCodeRepository.findByCode(code.toUpperCase())
                .orElseThrow(() -> ApiException.notFound("Gecersiz erisim kodu"));
        if (!accessCode.getElection().getId().equals(electionId)) {
            throw ApiException.badRequest("Bu erisim kodu bu secime ait degil");
        }

        Instant now = utcClock.instant();
        if (!accessCode.isValid(now)) {
            throw ApiException.badRequest("Bu erisim kodu aktif degil, suresi dolmus veya kullanim limiti dolmus");
        }

        accessCode.incrementUsage();
        accessCodeRepository.save(accessCode);

        List<CandidateResponse> candidates = candidateRepository
                .findByElectionIdAndIsDeletedFalseOrderByDisplayOrderAsc(electionId)
                .stream()
                .map(c -> CandidateResponse.builder()
                        .id(c.getId())
                        .name(c.getName())
                        .description(c.getDescription())
                        .imageUrl(c.getImageUrl())
                        .displayOrder(c.getDisplayOrder())
                        .candidateType(c.getCandidateType())
                        .memberUserId(c.getMemberUserId())
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
                .candidateCount(candidates.size())
                .candidates(candidates)
                .build();
    }

    /**
     * Compatibility endpoint for the current mobile flow.
     * The returned value is an eligibility credential, not a vote token, and is
     * never stored on the cast ballot. Future work should replace this with
     * proper blind-signature issuance.
     */
    @Transactional
    public VoteTokenResponse generateVoteToken(Long electionId, String userId) {
        Election election = findElectionOrThrow(electionId);
        if (!election.isActive()) {
            throw ApiException.badRequest("Bu secim su an aktif degil, oy kullanma belgesi alinamaz");
        }

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

        VoteToken credential = VoteToken.builder()
                .election(election)
                .userId(userId)
                .token(UUID.randomUUID().toString())
                .build();
        credential = voteTokenRepository.save(credential);

        return VoteTokenResponse.builder()
                .token(credential.getToken())
                .electionId(election.getId())
                .electionTitle(election.getTitle())
                .isUsed(false)
                .createdAt(credential.getCreatedAt())
                .build();
    }

    @Transactional
    public VoteResponse castVote(Long electionId, CastVoteRequest request) {
        Election election = findElectionOrThrow(electionId);
        if (!election.isActive()) {
            throw ApiException.badRequest("Bu secim su an aktif degil, oy kullanilamaz");
        }
        if (election.getElectionGuardContext() == null || election.getElectionGuardContext().isBlank()) {
            throw ApiException.badRequest("Bu secimde ElectionGuard context hazir degil");
        }

        var existingNullifier = voteNullifierRepository
                .findByElectionIdAndNullifierHash(electionId, request.getNullifierHash());
        if (existingNullifier.isPresent()) {
            Vote existingVote = voteRepository
                    .findByElectionIdAndBallotId(electionId, existingNullifier.get().getBallotId())
                    .orElseThrow(() -> new ApiException(HttpStatus.INTERNAL_SERVER_ERROR,
                            "Nullifier kaydi var ama anonim oy bulunamadi"));
            return VoteResponse.builder()
                    .electionId(election.getId())
                    .electionTitle(election.getTitle())
                    .alreadyVoted(true)
                    .votedAt(existingVote.getCastAt())
                    .trackingCode(existingVote.getTrackingCode())
                    .build();
        }

        VoteToken credential = validateAnonymousCredential(electionId, request);

        if (voteRepository.existsByElectionIdAndBallotId(electionId, request.getBallotId())) {
            throw ApiException.conflict("Bu ballot daha once kaydedilmis");
        }

        String ballotHash = sha256Hex(request.getEncryptedBallot());
        var validation = cryptoEngineClient.validateBallot(
                String.valueOf(electionId),
                election.getElectionGuardContext(),
                election.getElectionManifest(),
                request.getBallotId(),
                request.getEncryptedBallot(),
                request.getZkpProof(),
                request.getTrackingCode(),
                ballotHash
        );
        if (!validation.getValid()) {
            throw ApiException.badRequest("ElectionGuard ballot dogrulamasi basarisiz: " + validation.getError());
        }
        String trackingCode = validation.getTrackingCode().isBlank()
                ? request.getTrackingCode()
                : validation.getTrackingCode();
        if (trackingCode == null || trackingCode.isBlank()) {
            throw ApiException.badRequest("Tracking code zorunludur");
        }
        ballotHash = validation.getBallotHash().isBlank() ? ballotHash : validation.getBallotHash();

        Vote vote = Vote.builder()
                .election(election)
                .ballotId(request.getBallotId())
                .encryptedBallot(request.getEncryptedBallot())
                .trackingCode(trackingCode)
                .zkpProof(request.getZkpProof())
                .ballotHash(ballotHash)
                .build();
        vote = voteRepository.save(vote);

        voteNullifierRepository.save(VoteNullifier.builder()
                .election(election)
                .nullifierHash(request.getNullifierHash())
                .ballotId(request.getBallotId())
                .build());
        credential.markAsUsed(utcClock.instant());
        voteTokenRepository.save(credential);

        bulletinBoardClient.appendRecord(
                String.valueOf(electionId),
                "BALLOT_CAST",
                vote.getTrackingCode(),
                vote.getBallotHash(),
                vote.getEncryptedBallot()
        );

        log.info("Anonim E2E-V oy kaydedildi: election={}, ballot={}, hash={}",
                electionId, request.getBallotId(), ballotHash);

        return VoteResponse.builder()
                .electionId(election.getId())
                .electionTitle(election.getTitle())
                .alreadyVoted(false)
                .votedAt(vote.getCastAt())
                .trackingCode(vote.getTrackingCode())
                .build();
    }

    @Transactional(readOnly = true)
    public VoteResponse getMyVoteStatus(Long electionId, String userId) {
        Election election = findElectionOrThrow(electionId);
        var credential = voteTokenRepository.findByElectionIdAndUserId(electionId, userId);
        return VoteResponse.builder()
                .electionId(election.getId())
                .electionTitle(election.getTitle())
                .alreadyVoted(false)
                .votedAt(credential.map(VoteToken::getUsedAt).orElse(null))
                .build();
    }

    @Transactional(readOnly = true)
    public ElectionStatsResponse getElectionStats(Long electionId, String userId) {
        Election election = findElectionOrThrow(electionId);
        boolean isOwner = election.getCreatedBy().equals(userId);
        boolean isClosed = election.isClosed() || election.getStatus().name().equals("ARCHIVED");
        if (!isOwner && !isClosed) {
            throw ApiException.forbidden("Secim sonuclari henuz yayinlanmadi");
        }

        return ElectionStatsResponse.builder()
                .electionId(election.getId())
                .electionTitle(election.getTitle())
                .status(election.getStatus())
                .totalVotes(voteRepository.countByElectionId(electionId))
                .candidateStats(List.of())
                .build();
    }

    @Transactional(readOnly = true)
    public VoteProofResponse getMyVoteProof(Long electionId, String userId) {
        throw ApiException.badRequest("Tam E2E-V modunda kullanici kimligiyle oy kaniti getirilemez. Tracking code ile public bulletin endpoint'ini kullanin.");
    }

    @Transactional(readOnly = true)
    public VoteProofResponse getVoteProofByTrackingCode(Long electionId, String trackingCode) {
        Vote vote = voteRepository.findByElectionIdAndTrackingCode(electionId, trackingCode)
                .orElseThrow(() -> ApiException.notFound("Tracking code ile eslesen oy bulunamadi"));

        return VoteProofResponse.builder()
                .electionId(electionId)
                .ballotId(vote.getBallotId())
                .trackingCode(vote.getTrackingCode())
                .encryptedBallot(vote.getEncryptedBallot())
                .zkpProof(vote.getZkpProof())
                .ballotHash(vote.getBallotHash())
                .build();
    }

    private VoteToken validateAnonymousCredential(Long electionId, CastVoteRequest request) {
        if (request.getCredential().equals(request.getNullifierHash())) {
            throw ApiException.badRequest("Credential ve nullifier ayni olamaz");
        }
        if (request.getEncryptedBallot().contains("\"candidateId\"") || request.getEncryptedBallot().contains("candidate_id")) {
            throw ApiException.badRequest("Plain candidate id iceren oy kabul edilemez");
        }
        VoteToken credential = voteTokenRepository.findByToken(request.getCredential())
                .orElseThrow(() -> ApiException.forbidden("Gecersiz oy kullanma belgesi"));
        if (!credential.getElection().getId().equals(electionId)) {
            throw ApiException.forbidden("Oy kullanma belgesi bu secime ait degil");
        }
        if (credential.getIsUsed()) {
            throw ApiException.conflict("Bu oy kullanma belgesi daha once kullanilmis");
        }
        return credential;
    }

    private static String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 hesaplanamadi", e);
        }
    }

    private Election findElectionOrThrow(Long id) {
        return electionRepository.findById(id)
                .filter(e -> !e.getIsDeleted())
                .orElseThrow(() -> ApiException.notFound("Secim bulunamadi"));
    }
}
