package com.cepsandik.electionservice.dto.response;

import com.cepsandik.electionservice.enums.ElectionStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ElectionStatsResponse {

    private Long electionId;
    private String electionTitle;
    private ElectionStatus status;
    private Long totalVotes;
    private List<CandidateStatsResponse> candidateStats;
}
