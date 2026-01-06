package com.cepsandik.userservice.dtos.requests;

import jakarta.validation.constraints.NotNull;

/**
 * Kullanıcı durumu güncelleme request
 */
public record UpdateUserStatusRequest(
        @NotNull(message = "Durum belirtilmelidir") Boolean isActive) {
}
