package com.cepsandik.electionservice.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * Faz 1.4 — Spoil sonucu. {@code verified=true} ise crypto-engine ciphertext'i
 * cihazın açtığı nonce ile BAĞIMSIZ çözebildi; {@code decryptedSelections}
 * seçmene gösterilir: cihaz tam olarak ne şifrelemiş. Eşleşiyorsa cihaz
 * dürüsttür (cast-as-intended). Spoiled ballot tally'ye GİRMEZ.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SpoilBallotResponse {

    private boolean verified;
    private String ballotId;
    private String trackingCode;
    /** [{selectionId, vote}] — nonce ile sunucunun bağımsız çözdüğü plaintext. */
    private List<Map<String, Object>> decryptedSelections;
    private String message;
}
