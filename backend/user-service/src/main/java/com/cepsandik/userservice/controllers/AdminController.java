package com.cepsandik.userservice.controllers;

import com.cepsandik.userservice.dtos.requests.UpdatePlatformRoleRequest;
import com.cepsandik.userservice.dtos.requests.UpdateUserStatusRequest;
import com.cepsandik.userservice.dtos.responses.AdminUserResponse;
import com.cepsandik.userservice.dtos.responses.ApiResponse;
import com.cepsandik.userservice.dtos.responses.AuditLogResponse;
import com.cepsandik.userservice.dtos.responses.PlatformStatsResponse;
import com.cepsandik.userservice.exceptions.ApiException;
import com.cepsandik.userservice.service.AdminService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
@Tag(name = "Admin", description = "Platform yönetimi ve kullanıcı yönetim işlemleri (ADMIN rolü gerektirir)")
public class AdminController {

    private final AdminService adminService;

    /**
     * Platform rolü kontrolü yapar
     */
    private void validateAdminRole(String platformRole) {
        if (!"ADMIN".equals(platformRole)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Bu işlem için ADMIN yetkisi gereklidir");
        }
    }

    // ========== User Management ==========

    @Operation(summary = "Tüm kullanıcıları listeler")
    @GetMapping("/users")
    public ResponseEntity<ApiResponse<Page<AdminUserResponse>>> getAllUsers(
            @RequestHeader("X-User-Id") String userId,
            @RequestHeader("X-Platform-Role") String platformRole,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir) {

        validateAdminRole(platformRole);
        Page<AdminUserResponse> users = adminService.getAllUsers(page, size, sortBy, sortDir);
        return ResponseEntity.ok(ApiResponse.ok("Kullanıcılar listelendi", users));
    }

    @Operation(summary = "Kullanıcı detayını getirir")
    @GetMapping("/users/{id}")
    public ResponseEntity<ApiResponse<AdminUserResponse>> getUserById(
            @PathVariable UUID id,
            @RequestHeader("X-User-Id") String userId,
            @RequestHeader("X-Platform-Role") String platformRole) {

        validateAdminRole(platformRole);
        AdminUserResponse user = adminService.getUserById(id);
        return ResponseEntity.ok(ApiResponse.ok("Kullanıcı bilgileri getirildi", user));
    }

    @Operation(summary = "Kullanıcının platform rolünü günceller")
    @PutMapping("/users/{id}/role")
    public ResponseEntity<ApiResponse<AdminUserResponse>> updateUserRole(
            @PathVariable UUID id,
            @Valid @RequestBody UpdatePlatformRoleRequest request,
            @RequestHeader("X-User-Id") String userId,
            @RequestHeader("X-Platform-Role") String platformRole) {

        validateAdminRole(platformRole);
        AdminUserResponse user = adminService.updateUserRole(id, request.role(), userId);
        return ResponseEntity.ok(ApiResponse.ok("Kullanıcı rolü güncellendi", user));
    }

    @Operation(summary = "Kullanıcının aktif/pasif durumunu günceller")
    @PutMapping("/users/{id}/status")
    public ResponseEntity<ApiResponse<AdminUserResponse>> updateUserStatus(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateUserStatusRequest request,
            @RequestHeader("X-User-Id") String userId,
            @RequestHeader("X-Platform-Role") String platformRole) {

        validateAdminRole(platformRole);
        AdminUserResponse user = adminService.updateUserStatus(id, request.isActive(), userId);
        return ResponseEntity.ok(ApiResponse.ok("Kullanıcı durumu güncellendi", user));
    }

    @Operation(summary = "Platform istatistiklerini getirir")
    @GetMapping("/stats")
    public ResponseEntity<ApiResponse<PlatformStatsResponse>> getStats(
            @RequestHeader("X-User-Id") String userId,
            @RequestHeader("X-Platform-Role") String platformRole) {

        validateAdminRole(platformRole);
        PlatformStatsResponse stats = adminService.getStats();
        return ResponseEntity.ok(ApiResponse.ok("Platform istatistikleri getirildi", stats));
    }

    // ========== Audit Logs ==========

    @Operation(summary = "Tüm audit logları listeler")
    @GetMapping("/audit-logs")
    public ResponseEntity<ApiResponse<Page<AuditLogResponse>>> getAllAuditLogs(
            @RequestHeader("X-User-Id") String userId,
            @RequestHeader("X-Platform-Role") String platformRole,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {

        validateAdminRole(platformRole);
        Page<AuditLogResponse> logs = adminService.getAllAuditLogs(page, size);
        return ResponseEntity.ok(ApiResponse.ok("Audit logları listelendi", logs));
    }

    @Operation(summary = "Belirli bir kullanıcının audit loglarını listeler")
    @GetMapping("/audit-logs/user/{userId}")
    public ResponseEntity<ApiResponse<Page<AuditLogResponse>>> getAuditLogsByUser(
            @PathVariable("userId") UUID targetUserId,
            @RequestHeader("X-User-Id") String adminUserId,
            @RequestHeader("X-Platform-Role") String platformRole,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {

        validateAdminRole(platformRole);
        Page<AuditLogResponse> logs = adminService.getAuditLogsByUserId(targetUserId, page, size);
        return ResponseEntity.ok(ApiResponse.ok("Kullanıcı audit logları listelendi", logs));
    }

    @Operation(summary = "Action'a göre audit logları filtreler")
    @GetMapping("/audit-logs/action/{action}")
    public ResponseEntity<ApiResponse<Page<AuditLogResponse>>> getAuditLogsByAction(
            @PathVariable String action,
            @RequestHeader("X-User-Id") String userId,
            @RequestHeader("X-Platform-Role") String platformRole,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {

        validateAdminRole(platformRole);
        Page<AuditLogResponse> logs = adminService.getAuditLogsByAction(action, page, size);
        return ResponseEntity.ok(ApiResponse.ok("Audit logları filtrelendi", logs));
    }
}
