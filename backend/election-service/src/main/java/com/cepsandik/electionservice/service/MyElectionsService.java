package com.cepsandik.electionservice.service;

import com.cepsandik.electionservice.dto.response.MyElectionResponse;
import com.cepsandik.electionservice.entity.Election;
import com.cepsandik.electionservice.entity.VoteToken;
import com.cepsandik.electionservice.repository.CandidateRepository;
import com.cepsandik.electionservice.repository.ElectionRepository;
import com.cepsandik.electionservice.repository.VoteRepository;
import com.cepsandik.electionservice.repository.VoteTokenRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class MyElectionsService {

    private final ElectionRepository electionRepository;
    private final VoteTokenRepository voteTokenRepository;
    private final VoteRepository voteRepository;
    private final CandidateRepository candidateRepository;

    /**
     * Kullanıcının dahil olduğu aktif seçimleri listeler.
     * Kaynaklar:
     *  1) Kullanıcının oluşturduğu aktif/scheduled seçimler
     *  2) Kullanıcının vote token aldığı aktif seçimler
     */
    @Transactional(readOnly = true)
    public List<MyElectionResponse> getMyActiveElections(String userId) {
        Map<Long, MyElectionResponse> resultMap = new LinkedHashMap<>();

        // 1) Kullanıcının oluşturduğu aktif/scheduled seçimler
        List<Election> ownedElections = electionRepository.findActiveOrScheduledByCreatedBy(userId);
        for (Election election : ownedElections) {
            resultMap.put(election.getId(), mapToResponse(election, userId, true));
        }

        // 2) Kullanıcının vote token aldığı (henüz oy kullanmadığı) seçimler
        List<VoteToken> unusedTokens = voteTokenRepository.findByUserIdAndIsUsedFalseOrderByCreatedAtDesc(userId);
        for (VoteToken token : unusedTokens) {
            Election election = token.getElection();
            if (!election.getIsDeleted() && election.isActive() && !resultMap.containsKey(election.getId())) {
                resultMap.put(election.getId(), mapToResponse(election, userId, false));
            }
        }

        // 3) Kullanıcının oy kullandığı ama hâlâ aktif olan seçimler
        List<VoteToken> usedTokens = voteTokenRepository.findByUserIdAndIsUsedTrueOrderByUsedAtDesc(userId);
        for (VoteToken token : usedTokens) {
            Election election = token.getElection();
            if (!election.getIsDeleted() && election.isActive() && !resultMap.containsKey(election.getId())) {
                MyElectionResponse response = mapToResponse(election, userId, false);
                response.setHasVoted(true);
                response.setVotedAt(token.getUsedAt());
                resultMap.put(election.getId(), response);
            }
        }

        return new ArrayList<>(resultMap.values());
    }

    /**
     * Kullanıcının geçmiş seçim katılımlarını listeler.
     * Kapanmış/arşivlenmiş seçimler + oy kullanmış olduğu seçimler.
     */
    @Transactional(readOnly = true)
    public List<MyElectionResponse> getMyVotingHistory(String userId) {
        Map<Long, MyElectionResponse> resultMap = new LinkedHashMap<>();

        // 1) Kullanıcının oluşturduğu kapanmış seçimler
        List<Election> closedOwnedElections = electionRepository.findClosedByCreatedBy(userId);
        for (Election election : closedOwnedElections) {
            MyElectionResponse response = mapToResponse(election, userId, true);
            response.setTotalVotes(voteRepository.countByElectionId(election.getId()));
            resultMap.put(election.getId(), response);
        }

        // 2) Kullanıcının oy kullandığı seçimler (kapanmış olanlar)
        List<VoteToken> usedTokens = voteTokenRepository.findByUserIdAndIsUsedTrueOrderByUsedAtDesc(userId);
        for (VoteToken token : usedTokens) {
            Election election = token.getElection();
            if (!election.getIsDeleted() && !resultMap.containsKey(election.getId())
                    && (election.isClosed() || election.getStatus().name().equals("ARCHIVED"))) {
                MyElectionResponse response = mapToResponse(election, userId, false);
                response.setHasVoted(true);
                response.setVotedAt(token.getUsedAt());
                response.setTotalVotes(voteRepository.countByElectionId(election.getId()));
                resultMap.put(election.getId(), response);
            }
        }

        return new ArrayList<>(resultMap.values());
    }

    // ==================== Helper ====================

    private MyElectionResponse mapToResponse(Election election, String userId, boolean isOwner) {
        // Kullanıcının oy durumunu kontrol et
        var tokenOpt = voteTokenRepository.findByElectionIdAndUserId(election.getId(), userId);
        boolean hasVoted = tokenOpt.map(VoteToken::getIsUsed).orElse(false);

        return MyElectionResponse.builder()
                .electionId(election.getId())
                .electionTitle(election.getTitle())
                .electionDescription(election.getDescription())
                .communityId(election.getCommunityId())
                .status(election.getStatus())
                .type(election.getType())
                .startTime(election.getStartTime())
                .endTime(election.getEndTime())
                .candidateCount((int) candidateRepository.countByElectionIdAndIsDeletedFalse(election.getId()))
                .hasVoted(hasVoted)
                .votedAt(tokenOpt.map(VoteToken::getUsedAt).orElse(null))
                .isOwner(isOwner)
                .build();
    }
}
