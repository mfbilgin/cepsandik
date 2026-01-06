package com.cepsandik.userservice.service;

import com.cepsandik.userservice.common.MessageConstants;
import com.cepsandik.userservice.dtos.responses.AdminUserResponse;
import com.cepsandik.userservice.dtos.responses.AuditLogResponse;
import com.cepsandik.userservice.dtos.responses.PlatformStatsResponse;
import com.cepsandik.userservice.exceptions.ApiException;
import com.cepsandik.userservice.models.AuditLog;
import com.cepsandik.userservice.models.PlatformRole;
import com.cepsandik.userservice.models.User;
import com.cepsandik.userservice.repositories.AuditLogRepository;
import com.cepsandik.userservice.repositories.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Admin paneli için kullanıcı yönetim servisi
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AdminService {

    private final UserRepository userRepository;
    private final AuditLogRepository auditLogRepository;

    /**
     * Tüm kullanıcıları sayfalı olarak getirir
     */
    public Page<AdminUserResponse> getAllUsers(int page, int size, String sortBy, String sortDir) {
        Sort sort = sortDir.equalsIgnoreCase("desc")
                ? Sort.by(sortBy).descending()
                : Sort.by(sortBy).ascending();

        Pageable pageable = PageRequest.of(page, size, sort);
        Page<User> users = userRepository.findAll(pageable);

        return users.map(this::toAdminUserResponse);
    }

    /**
     * Kullanıcı detayını getirir
     */
    public AdminUserResponse getUserById(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, MessageConstants.USER_NOT_FOUND));

        return toAdminUserResponse(user);
    }

    /**
     * Kullanıcının platform rolünü günceller
     */
    @Transactional
    public AdminUserResponse updateUserRole(UUID userId, PlatformRole newRole, String adminUserId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, MessageConstants.USER_NOT_FOUND));

        // Kendi rolünü değiştiremesin
        if (user.getId().toString().equals(adminUserId)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Kendi rolünüzü değiştiremezsiniz");
        }

        user.setPlatformRole(newRole);
        User updated = userRepository.save(user);

        log.info("Kullanıcı rolü güncellendi: userId={}, newRole={}, byAdmin={}",
                userId, newRole, adminUserId);

        return toAdminUserResponse(updated);
    }

    /**
     * Kullanıcının aktif/pasif durumunu günceller
     */
    @Transactional
    public AdminUserResponse updateUserStatus(UUID userId, boolean isActive, String adminUserId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, MessageConstants.USER_NOT_FOUND));

        // Kendi hesabını pasif yapmasın
        if (user.getId().toString().equals(adminUserId) && !isActive) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Kendi hesabınızı pasif yapamazsınız");
        }

        user.setActive(isActive);
        User updated = userRepository.save(user);

        log.info("Kullanıcı durumu güncellendi: userId={}, isActive={}, byAdmin={}",
                userId, isActive, adminUserId);

        return toAdminUserResponse(updated);
    }

    /**
     * Platform istatistiklerini hesaplar
     */
    public PlatformStatsResponse getStats() {
        long totalUsers = userRepository.count();
        long activeUsers = userRepository.countByIsActiveTrue();
        long verifiedUsers = userRepository.countByIsVerifiedTrue();
        long adminUsers = userRepository.countByPlatformRole(PlatformRole.ADMIN);
        long deletedUsers = userRepository.countByDeletedAtIsNotNull();

        LocalDateTime last24Hours = LocalDateTime.now().minusHours(24);
        LocalDateTime last7Days = LocalDateTime.now().minusDays(7);

        long usersLast24Hours = userRepository.countByCreatedAtAfter(last24Hours);
        long usersLast7Days = userRepository.countByCreatedAtAfter(last7Days);

        return new PlatformStatsResponse(
                totalUsers,
                activeUsers,
                verifiedUsers,
                adminUsers,
                deletedUsers,
                usersLast24Hours,
                usersLast7Days);
    }

    // ========== Audit Log Methods ==========

    /**
     * Tüm audit logları sayfalı olarak getirir
     */
    public Page<AuditLogResponse> getAllAuditLogs(int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        Page<AuditLog> logs = auditLogRepository.findAllByOrderByTimestampDesc(pageable);
        return logs.map(this::toAuditLogResponse);
    }

    /**
     * Belirli bir kullanıcının audit loglarını getirir
     */
    public Page<AuditLogResponse> getAuditLogsByUserId(UUID userId, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        Page<AuditLog> logs = auditLogRepository.findByUserIdOrderByTimestampDesc(userId, pageable);
        return logs.map(this::toAuditLogResponse);
    }

    /**
     * Belirli bir action'a göre audit logları filtreler
     */
    public Page<AuditLogResponse> getAuditLogsByAction(String action, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        Page<AuditLog> logs = auditLogRepository.findByActionContainingIgnoreCaseOrderByTimestampDesc(action, pageable);
        return logs.map(this::toAuditLogResponse);
    }

    private AdminUserResponse toAdminUserResponse(User user) {
        return new AdminUserResponse(
                user.getId(),
                user.getFirstName(),
                user.getLastName(),
                user.getEmail(),
                user.getPlatformRole().name(),
                user.isActive(),
                user.isVerified(),
                user.getVerifiedAt(),
                user.getCreatedAt(),
                user.getUpdatedAt(),
                user.getDeletedAt());
    }

    private AuditLogResponse toAuditLogResponse(AuditLog log) {
        return new AuditLogResponse(
                log.getId(),
                log.getUserId(),
                log.getAction(),
                log.getDetails(),
                log.getIpAddress(),
                log.getTimestamp());
    }
}
