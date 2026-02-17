package com.cepsandik.electionservice.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VoteResponse {

    private Long electionId;
    private String electionTitle;
    private Long candidateId;
    private String candidateName;
    private Boolean alreadyVoted;
    private LocalDateTime votedAt;
}
