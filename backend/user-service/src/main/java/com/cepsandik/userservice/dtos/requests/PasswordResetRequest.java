package com.cepsandik.userservice.dtos.requests;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record PasswordResetRequest(
                @NotBlank(message = "E-posta alanı zorunludur") @Email(message = "Geçerli bir e-posta adresi giriniz") String email) {
}
