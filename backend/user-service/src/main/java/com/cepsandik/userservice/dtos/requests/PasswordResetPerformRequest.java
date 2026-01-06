package com.cepsandik.userservice.dtos.requests;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PasswordResetPerformRequest(
                @NotBlank(message = "Sıfırlama token'ı zorunludur") String resetToken,

                @NotBlank(message = "Yeni parola alanı zorunludur") @Size(min = 8, max = 128, message = "Yeni parola 8 ile 128 karakter arasında olmalıdır") String newPassword) {
}
