package com.cepsandik.electionservice.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CandidateResultResponse {

    private String selectionId;
    private String optionId;
    private String candidateName;
    private String candidateDescription;
    private String candidateImageUrl;
    private Long voteCount;
    private Double percentage;
    private Integer rank;
    private Boolean isWinner;
}
