package com.cepsandik.userservice.dtos.requests;

import com.cepsandik.userservice.models.PlatformRole;
import jakarta.validation.constraints.NotNull;

/**
 * Platform rolü güncelleme request
 */
public record UpdatePlatformRoleRequest(
        @NotNull(message = "Rol belirtilmelidir") PlatformRole role) {
}
