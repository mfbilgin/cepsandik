package com.cepsandik.electionservice.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * Faz 1.4 — Benaloh challenge: seçmenin spoil ettiği ballot.
 * Cihaz şifreli ballot'u + onu üreten primary nonce'ı açar; sunucu
 * (crypto-engine) nonce ile bağımsız çözüp cihazın dürüstlüğünü doğrular.
 */
@Data
public class SpoilBallotRequest {

    @NotBlank
    private String ballotId;

    @NotBlank
    private String encryptedBallot;

    /** Cihazın açtığı ballot primary nonce (hex). */
    @NotBlank
    private String primaryNonce;

    /** Cihazın açtığı PlaintextBallot (KMP PlaintextBallotJson) — re-encrypt için. */
    @NotBlank
    private String plaintextBallot;

    private String trackingCode;
    private String ballotHash;
}
