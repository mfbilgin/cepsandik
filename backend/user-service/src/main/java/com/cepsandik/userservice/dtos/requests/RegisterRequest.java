package com.cepsandik.userservice.dtos.requests;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
                @NotBlank(message = "Ad alanı zorunludur") @Size(max = 50, message = "Ad en fazla 50 karakter olabilir") String firstName,

                @NotBlank(message = "Soyad alanı zorunludur") @Size(max = 50, message = "Soyad en fazla 50 karakter olabilir") String lastName,

                @NotBlank(message = "E-posta alanı zorunludur") @Email(message = "Geçerli bir e-posta adresi giriniz") @Size(max = 255, message = "E-posta en fazla 255 karakter olabilir") String email,

                @NotBlank(message = "Parola alanı zorunludur") @Size(min = 8, max = 128, message = "Parola 8 ile 128 karakter arasında olmalıdır") String password) {
}
