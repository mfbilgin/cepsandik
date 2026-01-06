package com.cepsandik.userservice.dtos.requests;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * Request for 2FA verification
 */
public record TwoFactorVerifyRequest(
        @NotBlank(message = "Doğrulama kodu zorunludur") @Pattern(regexp = "^[0-9]{6}$", message = "Doğrulama kodu 6 haneli olmalıdır") String code) {
}
