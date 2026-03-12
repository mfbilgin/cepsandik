package com.cepsandik.electionservice.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

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
}
