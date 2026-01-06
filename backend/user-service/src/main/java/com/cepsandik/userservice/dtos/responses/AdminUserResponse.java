package com.cepsandik.userservice.dtos.responses;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Admin paneli için kullanıcı detay response
 */
public record AdminUserResponse(
    UUID id,
    String firstName,
    String lastName,
    String email,
    String platformRole,
    boolean isActive,
    boolean isVerified,
    LocalDateTime verifiedAt,
    LocalDateTime createdAt,
    LocalDateTime updatedAt,
    LocalDateTime deletedAt
) {}
