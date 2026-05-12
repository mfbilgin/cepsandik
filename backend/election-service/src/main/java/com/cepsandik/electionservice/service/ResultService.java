package com.cepsandik.electionservice.service;

import com.cepsandik.electionservice.client.CommunityServiceClient;
import com.cepsandik.electionservice.dto.response.CandidateResultResponse;
import com.cepsandik.electionservice.dto.response.ElectionResultResponse;
import com.cepsandik.electionservice.entity.Candidate;
import com.cepsandik.electionservice.entity.Election;
import com.cepsandik.electionservice.entity.ElectionResult;
import com.cepsandik.electionservice.enums.ElectionStatus;
import com.cepsandik.electionservice.exception.ApiException;
import com.cepsandik.electionservice.repository.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

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
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Transactional
    public ElectionResultResponse calculateResults(Long electionId, String userId) {
        Election election = findElectionOrThrow(electionId);
        if (!election.getCreatedBy().equals(userId)) {
            throw ApiException.forbidden("Bu islem icin yetkiniz yok");
        }
        if (!election.isClosed() && election.getStatus() != ElectionStatus.ARCHIVED) {
            throw ApiException.badRequest("Sonuclar sadece kapanmis secimler icin hesaplanabilir");
        }
        if (election.getTallyResults() == null || election.getTallyResults().isBlank()) {
            throw ApiException.badRequest("Kriptografik tally henuz uretilmedi. Guardian quorum decryption tamamlanmadan resmi sonuc hesaplanamaz.");
        }

        if (electionResultRepository.existsByElectionId(electionId)) {
            electionResultRepository.deleteByElectionId(electionId);
        }

        List<ElectionResult> results = buildResultsFromCryptographicTally(election);
        electionResultRepository.saveAll(results);
        log.info("Kriptografik tally sonuc snapshot'i kaydedildi: election={}, selections={}", electionId, results.size());

        return buildResultResponse(election, results, voteRepository.countByElectionId(electionId),
                voteTokenRepository.countByElectionId(electionId));
    }

    @Transactional(readOnly = true)
    public ElectionResultResponse getResults(Long electionId, String userId) {
        Election election = findElectionOrThrow(electionId);
        boolean isOwner = election.getCreatedBy().equals(userId);
        boolean isClosed = election.isClosed() || election.getStatus() == ElectionStatus.ARCHIVED;
        if (!isClosed) {
            throw ApiException.badRequest("Sonuclar henuz yayinlanmadi");
        }
        if (!election.getResultsPublic() && !isOwner) {
            throw ApiException.forbidden("Bu secimin sonuclari herkese acik degil");
        }
        if (!electionResultRepository.existsByElectionId(electionId)) {
            throw ApiException.notFound("Kriptografik sonuc snapshot'i henuz olusturulmadi");
        }

        List<ElectionResult> results = electionResultRepository.findByElectionIdOrderByRankAsc(electionId);
        return buildResultResponse(election, results, voteRepository.countByElectionId(electionId),
                voteTokenRepository.countByElectionId(electionId));
    }

    @Transactional
    public ElectionResultResponse publishResults(Long electionId, String userId) {
        Election election = findElectionOrThrow(electionId);
        if (!election.getCreatedBy().equals(userId)) {
            throw ApiException.forbidden("Bu islem icin yetkiniz yok");
        }
        if (!election.isClosed()) {
            throw ApiException.badRequest("Sadece CLOSED durumundaki secimlerin sonuclari yayinlanabilir");
        }
        if (!electionResultRepository.existsByElectionId(electionId)) {
            calculateResults(electionId, userId);
        }

        election.setStatus(ElectionStatus.ARCHIVED);
        election.setResultsPublic(true);
        electionRepository.save(election);

        try {
            List<String> memberUserIds = election.getCommunityId() != null
                    ? communityServiceClient.getMemberUserIds(election.getCommunityId())
                    : List.of();
            if (!memberUserIds.isEmpty()) {
                notificationProducer.notifyResultsPublished(election.getId(), election.getTitle(),
                        election.getCommunityId(), memberUserIds);
            }
        } catch (Exception e) {
            log.error("Sonuc yayinlama bildirimi gonderilemedi: electionId={}, hata={}",
                    electionId, e.getMessage());
        }

        List<ElectionResult> results = electionResultRepository.findByElectionIdOrderByRankAsc(electionId);
        return buildResultResponse(election, results, voteRepository.countByElectionId(electionId),
                voteTokenRepository.countByElectionId(electionId));
    }

    private List<ElectionResult> buildResultsFromCryptographicTally(Election election) {
        try {
            Map<String, Candidate> candidateBySelection = candidateRepository
                    .findByElectionIdAndIsDeletedFalseOrderByDisplayOrderAsc(election.getId())
                    .stream()
                    .collect(Collectors.toMap(c -> "candidate_" + c.getId(), c -> c));

            List<ElectionResult> results = new ArrayList<>();
            JsonNode root = objectMapper.readTree(election.getTallyResults());
            JsonNode contests = root.isArray() ? root : root.path("results");
            for (JsonNode contest : contests) {
                JsonNode selections = contest.path("selections");
                if (!selections.isArray()) {
                    continue;
                }
                for (JsonNode selection : selections) {
                    String selectionId = selection.path("selection_id").asText(selection.path("selectionId").asText());
                    long tally = selection.path("tally").asLong(selection.path("vote_count").asLong(0L));
                    Candidate candidate = candidateBySelection.get(selectionId);
                    results.add(ElectionResult.builder()
                            .election(election)
                            .selectionId(selectionId)
                            .optionLabel(candidate != null ? candidate.getName() : selectionId)
                            .optionDescription(candidate != null ? candidate.getDescription() : null)
                            .optionImageUrl(candidate != null ? candidate.getImageUrl() : null)
                            .voteCount(tally)
                            .build());
                }
            }

            long totalVotes = results.stream().mapToLong(ElectionResult::getVoteCount).sum();
            results.sort(Comparator.comparing(ElectionResult::getVoteCount).reversed());
            for (int i = 0; i < results.size(); i++) {
                ElectionResult result = results.get(i);
                result.setRank(i + 1);
                result.setPercentage(totalVotes > 0 ? Math.round(((double) result.getVoteCount() / totalVotes * 100) * 100.0) / 100.0 : 0.0);
            }
            return results;
        } catch (Exception e) {
            throw ApiException.badRequest("Kriptografik tally sonucu parse edilemedi: " + e.getMessage());
        }
    }

    private ElectionResultResponse buildResultResponse(Election election, List<ElectionResult> results,
            long totalVotes, long totalTokens) {
        List<CandidateResultResponse> candidateResults = results.stream()
                .map(r -> CandidateResultResponse.builder()
                        .selectionId(r.getSelectionId())
                        .optionId(r.getSelectionId())
                        .candidateName(r.getOptionLabel())
                        .candidateDescription(r.getOptionDescription())
                        .candidateImageUrl(r.getOptionImageUrl())
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
                .orElseThrow(() -> ApiException.notFound("Secim bulunamadi"));
    }
}
