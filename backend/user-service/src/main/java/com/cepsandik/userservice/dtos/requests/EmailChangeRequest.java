package com.cepsandik.userservice.dtos.requests;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/**
 * Email değişikliği isteği
 */
public record EmailChangeRequest(
        @NotBlank(message = "Yeni email adresi zorunludur") @Email(message = "Geçerli bir email adresi giriniz") String newEmail,

        @NotBlank(message = "Mevcut parola zorunludur") String currentPassword) {
}
