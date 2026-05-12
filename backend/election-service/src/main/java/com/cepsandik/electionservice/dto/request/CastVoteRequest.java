package com.cepsandik.electionservice.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CastVoteRequest {

    @NotBlank(message = "Ballot ID zorunludur")
    private String ballotId;

    @NotBlank(message = "Anonim oy kullanma belgesi zorunludur")
    private String credential;

    @NotBlank(message = "Oy kullanma belgesi imzasi zorunludur")
    private String credentialSignature;

    @NotBlank(message = "Nullifier hash zorunludur")
    private String nullifierHash;

    @NotBlank(message = "Sifreli oy zorunludur")
    private String encryptedBallot;

    @NotBlank(message = "ZKP kaniti zorunludur")
    private String zkpProof;

    @NotBlank(message = "Tracking code zorunludur")
    private String trackingCode;
}
