package com.cepsandik.electionservice.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VoteResponse {

    private Long electionId;
    private String electionTitle;
    private Boolean alreadyVoted;
    private Instant votedAt;

    /** ElectionGuard doğrulama özeti (şifreli oy yoksa null olabilir) */
    private String trackingCode;
}
