package com.cepsandik.userservice.dtos.requests;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record IntegrityVerifyRequest(
        @NotBlank(message = "Challenge zorunludur") @Size(max = 128) String nonce,
        @NotBlank(message = "Bütünlük token'ı zorunludur") @Size(max = 16384) String token) {
}
