package com.cepsandik.electionservice.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CandidateStatsResponse {

    private Long candidateId;
    private String candidateName;
    private Long voteCount;
    private Double percentage;
}
