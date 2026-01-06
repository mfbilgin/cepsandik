package com.cepsandik.userservice.dtos.responses;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Audit log response DTO
 */
public record AuditLogResponse(
        Long id,
        UUID userId,
        String action,
        String details,
        String ipAddress,
        LocalDateTime timestamp) {
}
