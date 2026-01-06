package com.cepsandik.userservice.dtos.requests;

import jakarta.validation.constraints.NotBlank;

public record TokenRefreshRequest(
        @NotBlank(message = "Yenileme token'ı zorunludur") String refreshToken) {
}
