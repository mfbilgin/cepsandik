package com.cepsandik.electionservice.dto.response;

import com.cepsandik.electionservice.enums.ElectionStatus;
import com.cepsandik.electionservice.enums.ElectionType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ElectionResultResponse {

    private Long electionId;
    private String electionTitle;
    private String electionDescription;
    private ElectionStatus status;
    private ElectionType type;
    private Instant startTime;
    private Instant endTime;
    private Long totalVotes;
    private Long totalEligibleVoters;
    private Double turnoutPercentage;
    private LocalDateTime resultsCalculatedAt;
    private List<CandidateResultResponse> candidateResults;
}
