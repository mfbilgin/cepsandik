package com.cepsandik.bulletinboard.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class AppendRecordRequest {
    @NotBlank
    private String electionId;
    @NotBlank
    private String recordType;
    private String trackingCode;
    private String ballotHash;
    /** Boş olabilir — bazı şeffaflık kayıtları (KEY_UPLOADED, DECLINED) payload taşımaz. */
    private String payload;
    /**
     * İstemci tarafı idempotency anahtarı (outbox satır UUID'si). Aynı anahtarla
     * tekrar gelen append no-op'tur (publisher retry'da zincir çift kayıt olmasın).
     */
    private String idempotencyKey;
}
