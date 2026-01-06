package com.cepsandik.userservice.dtos.responses;

/**
 * Platform istatistikleri response
 */
public record PlatformStatsResponse(
    long totalUsers,
    long activeUsers,
    long verifiedUsers,
    long adminUsers,
    long deletedUsers,
    long usersLast24Hours,
    long usersLast7Days
) {}
