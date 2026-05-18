package com.cepsandik.userservice.service;

import com.cepsandik.userservice.models.AuditLog;
import com.cepsandik.userservice.repositories.AuditLogRepository;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuditService {

    private final AuditLogRepository auditLogRepository;

    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void log(UUID userId, String module, String action, String status, AuditLog.LogLevel level, String details, HttpServletRequest request) {
        try {
            String ip = getClientIp(request);
            String ua = request != null ? request.getHeader("User-Agent") : "System";

            AuditLog auditEntry = AuditLog.builder()
                    .userId(userId)
                    .module(module)
                    .action(action)
                    .status(status)
                    .level(level)
                    .details(details)
                    .ipAddress(ip)
                    .userAgent(ua)
                    .build();

            auditLogRepository.save(auditEntry);
            
            // Konsola da kritik seviyede yazalım
            if (level == AuditLog.LogLevel.ERROR || level == AuditLog.LogLevel.CRITICAL) {
                log.error("[AUDIT ERROR] Module: {}, Action: {}, Status: {}, Details: {}", module, action, status, details);
            }
        } catch (Exception e) {
            log.error("Audit Log kaydı veritabanına yapılamadı: {}", e.getMessage());
        }
    }

    /**
     * Faz 4.15 — Pre-existing build break fix: AuditService.log 7-arg'a
     * çevrilmiş (commit 5b94eff7) ama AuditLoggingAspect/RateLimitFilter/
     * JwtAuthenticationEntryPoint hâlâ 4-arg çağırıyordu → user-service o
     * commit'ten beri DERLENMİYORDU (prod imkansız). Bu convenience overload
     * ip'i doğrudan alır (request yok), makul default'larla kaydeder.
     */
    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void log(UUID userId, String action, String details, String ip) {
        try {
            AuditLog auditEntry = AuditLog.builder()
                    .userId(userId)
                    .module("GENERAL")
                    .action(action)
                    .status("N/A")
                    .level(AuditLog.LogLevel.INFO)
                    .details(details)
                    .ipAddress(ip != null ? ip : "127.0.0.1")
                    .userAgent("System")
                    .build();
            auditLogRepository.save(auditEntry);
        } catch (Exception e) {
            log.error("Audit Log kaydı veritabanına yapılamadı: {}", e.getMessage());
        }
    }

    private String getClientIp(HttpServletRequest request) {
        if (request == null) return "127.0.0.1";
        String remoteAddr = request.getHeader("X-FORWARDED-FOR");
        if (remoteAddr == null || "".equals(remoteAddr)) {
            remoteAddr = request.getRemoteAddr();
        }
        return remoteAddr;
    }
}
