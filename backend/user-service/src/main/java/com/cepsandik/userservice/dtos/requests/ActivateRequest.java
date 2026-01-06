package com.cepsandik.userservice.dtos.requests;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record ActivateRequest(
                @NotBlank(message = "E-posta alanı zorunludur") @Email(message = "Geçerli bir e-posta adresi giriniz") String email,

                @NotBlank(message = "Parola alanı zorunludur") String password) {
}
