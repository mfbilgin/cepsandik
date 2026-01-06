package com.cepsandik.userservice.dtos.requests;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * Login request with optional 2FA code
 */
public record LoginWith2FARequest(
        @NotBlank(message = "Email zorunludur") @Email(message = "Geçerli bir email adresi giriniz") String email,

        @NotBlank(message = "Parola zorunludur") String password,

        @Pattern(regexp = "^[0-9]{6,8}$", message = "Doğrulama kodu 6-8 haneli olmalıdır") String twoFactorCode) {
}
