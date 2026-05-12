package com.cepsandik.electionservice.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ElectionProofResponse {
    private Long electionId;
    private String electionGuardContext;
    private String electionManifest;
    private String guardianRecords;
    private String tallyProof;
    private List<BallotProofResponse> ballots;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class BallotProofResponse {
        private String ballotId;
        private String trackingCode;
        private String encryptedBallot;
        private String zkpProof;
        private String ballotHash;
    }
}
