package com.cepsandik.electionservice.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CastVoteRequest {

    @NotBlank(message = "Oy token'ı zorunludur")
    private String voteToken;

    @NotNull(message = "Aday ID zorunludur")
    private Long candidateId;

    /** RSA ile şifrelenmiş oy verisi (Crypto-Engine entegrasyonu) */
    private byte[] rsaEncryptedPayload;
}
