package com.cepsandik.electionservice.service;

import com.cepsandik.electionservice.dto.response.CandidateResultResponse;
import com.cepsandik.electionservice.dto.response.ElectionResultResponse;
import com.cepsandik.electionservice.entity.Candidate;
import com.cepsandik.electionservice.entity.Election;
import com.cepsandik.electionservice.entity.ElectionResult;
import com.cepsandik.electionservice.enums.ElectionStatus;
import com.cepsandik.electionservice.exception.ApiException;
import com.cepsandik.electionservice.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import com.cepsandik.electionservice.client.CommunityServiceClient;

@Service
@RequiredArgsConstructor
@Slf4j
public class ResultService {

    private final ElectionRepository electionRepository;
    private final CandidateRepository candidateRepository;
    private final VoteRepository voteRepository;
    private final VoteTokenRepository voteTokenRepository;
    private final ElectionResultRepository electionResultRepository;
    private final ElectionNotificationProducer notificationProducer;
    private final CommunityServiceClient communityServiceClient;

    /**
     * Seçim sonuçlarını hesaplar ve kaydeder.
     * Sadece CLOSED durumundaki seçimler için çalışır.
     * Zaten hesaplanmışsa yeniden hesaplar (idempotent).
     */
    @Transactional
    public ElectionResultResponse calculateResults(Long electionId, String userId) {
        Election election = findElectionOrThrow(electionId);

        // Yetki kontrolü: sadece seçim sahibi veya ADMIN
        if (!election.getCreatedBy().equals(userId)) {
            throw ApiException.forbidden("Bu işlem için yetkiniz yok");
        }

        // Seçim CLOSED veya ARCHIVED olmalı
        if (!election.isClosed() && election.getStatus() != ElectionStatus.ARCHIVED) {
            throw ApiException.badRequest("Sonuçlar sadece kapanmış seçimler için hesaplanabilir");
        }

        // Mevcut sonuçları sil (yeniden hesaplama)
        if (electionResultRepository.existsByElectionId(electionId)) {
            electionResultRepository.deleteByElectionId(electionId);
            log.info("Mevcut sonuçlar silindi, yeniden hesaplanıyor - Election: {}", electionId);
        }

        // Oy sayılarını hesapla
        List<Object[]> voteCounts = voteRepository.countVotesByCandidateForElection(electionId);
        long totalVotes = voteRepository.countByElectionId(electionId);
        long totalTokens = voteTokenRepository.countByElectionId(electionId);

        // Tüm adayları getir (oy almamış olanlar dahil)
        List<Candidate> allCandidates = candidateRepository
                .findByElectionIdAndIsDeletedFalseOrderByDisplayOrderAsc(electionId);

        // Sonuçları oluştur
        List<ElectionResult> results = new ArrayList<>();
        int rank = 1;

        // Önce oy almış adayları sıralı ekle
        for (Object[] row : voteCounts) {
            Long candidateId = (Long) row[0];
            Long count = (Long) row[1];
            Candidate candidate = allCandidates.stream()
                    .filter(c -> c.getId().equals(candidateId))
                    .findFirst().orElse(null);

            if (candidate != null) {
                double pct = totalVotes > 0 ? (double) count / totalVotes * 100 : 0;
                ElectionResult result = ElectionResult.builder()
                        .election(election)
                        .candidate(candidate)
                        .voteCount(count)
                        .percentage(Math.round(pct * 100.0) / 100.0) // 2 ondalık
                        .rank(rank++)
                        .build();
                results.add(result);
            }
        }

        // Oy almamış adayları da ekle
        for (Candidate candidate : allCandidates) {
            boolean alreadyAdded = results.stream()
                    .anyMatch(r -> r.getCandidate().getId().equals(candidate.getId()));
            if (!alreadyAdded) {
                ElectionResult result = ElectionResult.builder()
                        .election(election)
                        .candidate(candidate)
                        .voteCount(0L)
                        .percentage(0.0)
                        .rank(rank++)
                        .build();
                results.add(result);
            }
        }

        // Kaydet
        electionResultRepository.saveAll(results);
        log.info("Sonuçlar hesaplandı - Election: {}, Toplam oy: {}", electionId, totalVotes);

        return buildResultResponse(election, results, totalVotes, totalTokens);
    }

    /**
     * Seçim sonuçlarını görüntüler.
     * resultsPublic = true ise herkes görebilir, değilse sadece seçim sahibi.
     */
    @Transactional(readOnly = true)
    public ElectionResultResponse getResults(Long electionId, String userId) {
        Election election = findElectionOrThrow(electionId);

        // Yetki kontrolü
        boolean isOwner = election.getCreatedBy().equals(userId);
        boolean isClosed = election.isClosed() || election.getStatus() == ElectionStatus.ARCHIVED;

        if (!isClosed) {
            throw ApiException.badRequest("Sonuçlar henüz yayınlanmadı");
        }

        if (!election.getResultsPublic() && !isOwner) {
            throw ApiException.forbidden("Bu seçimin sonuçları herkese açık değil");
        }

        // Sonuçlar hesaplanmış mı
        if (!electionResultRepository.existsByElectionId(electionId)) {
            throw ApiException.notFound("Sonuçlar henüz hesaplanmamış. Seçim sahibi sonuçları hesaplamalıdır.");
        }

        List<ElectionResult> results = electionResultRepository.findByElectionIdOrderByRankAsc(electionId);
        long totalVotes = voteRepository.countByElectionId(electionId);
        long totalTokens = voteTokenRepository.countByElectionId(electionId);

        return buildResultResponse(election, results, totalVotes, totalTokens);
    }

    /**
     * Sonuçları yayınlar ve seçimi ARCHIVED durumuna geçirir.
     */
    @Transactional
    public ElectionResultResponse publishResults(Long electionId, String userId) {
        Election election = findElectionOrThrow(electionId);

        if (!election.getCreatedBy().equals(userId)) {
            throw ApiException.forbidden("Bu işlem için yetkiniz yok");
        }

        if (!election.isClosed()) {
            throw ApiException.badRequest("Sadece CLOSED durumundaki seçimlerin sonuçları yayınlanabilir");
        }

        // Sonuçlar henüz hesaplanmamışsa otomatik hesapla
        if (!electionResultRepository.existsByElectionId(electionId)) {
            calculateResults(electionId, userId);
        }

        // Seçimi ARCHIVED durumuna geçir
        election.setStatus(ElectionStatus.ARCHIVED);
        election.setResultsPublic(true);
        electionRepository.save(election);

        log.info("Sonuçlar yayınlandı, seçim arşivlendi - Election: {}", electionId);

        // Topluluk üyelerine bildirim gönder
        try {
            List<String> memberUserIds = communityServiceClient
                    .getMemberUserIds(election.getCommunityId());
            if (!memberUserIds.isEmpty()) {
                notificationProducer.notifyResultsPublished(
                        election.getId(), election.getTitle(),
                        election.getCommunityId(), memberUserIds);
            }
        } catch (Exception e) {
            log.error("Sonuç yayınlama bildirimi gönderilemedi: electionId={}, hata={}",
                    electionId, e.getMessage());
        }

        List<ElectionResult> results = electionResultRepository.findByElectionIdOrderByRankAsc(electionId);
        long totalVotes = voteRepository.countByElectionId(electionId);
        long totalTokens = voteTokenRepository.countByElectionId(electionId);

        return buildResultResponse(election, results, totalVotes, totalTokens);
    }

    // ==================== Helper ====================

    private ElectionResultResponse buildResultResponse(Election election, List<ElectionResult> results,
            long totalVotes, long totalTokens) {
        List<CandidateResultResponse> candidateResults = results.stream()
                .map(r -> CandidateResultResponse.builder()
                        .candidateId(r.getCandidate().getId())
                        .candidateName(r.getCandidate().getName())
                        .candidateDescription(r.getCandidate().getDescription())
                        .candidateImageUrl(r.getCandidate().getImageUrl())
                        .voteCount(r.getVoteCount())
                        .percentage(r.getPercentage())
                        .rank(r.getRank())
                        .isWinner(r.getRank() == 1 && r.getVoteCount() > 0)
                        .build())
                .collect(Collectors.toList());

        double turnout = totalTokens > 0 ? (double) totalVotes / totalTokens * 100 : 0;

        return ElectionResultResponse.builder()
                .electionId(election.getId())
                .electionTitle(election.getTitle())
                .electionDescription(election.getDescription())
                .status(election.getStatus())
                .type(election.getType())
                .startTime(election.getStartTime())
                .endTime(election.getEndTime())
                .totalVotes(totalVotes)
                .totalEligibleVoters(totalTokens)
                .turnoutPercentage(Math.round(turnout * 100.0) / 100.0)
                .resultsCalculatedAt(results.isEmpty() ? null : results.get(0).getCreatedAt())
                .candidateResults(candidateResults)
                .build();
    }

    private Election findElectionOrThrow(Long id) {
        return electionRepository.findById(id)
                .filter(e -> !e.getIsDeleted())
                .orElseThrow(() -> ApiException.notFound("Seçim bulunamadı"));
    }
}
