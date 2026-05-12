package com.cepsandik.electionservice.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SubmitGuardianKeyRequest {

    @NotBlank(message = "Public key boş olamaz")
    private String publicKey;

    @NotBlank(message = "Taahhütler boş olamaz")
    private String commitments;

    /** EG 2.1 Schnorr Proofs */
    private String coefficientProofs;

    /** EG 2.1 backups (opsiyonel şimdilik) */
    private String keyBackups;
}
