package com.cepsandik.electionservice.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VoteProofResponse {
    private Long electionId;
    private String ballotId;
    private String trackingCode;
    private String encryptedBallot;
    private String zkpProof;
    private String ballotHash;
}
