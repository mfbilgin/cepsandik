package com.cepsandik.userservice.aspects;

import com.cepsandik.userservice.annotations.LogAudit;
import com.cepsandik.userservice.dtos.responses.AuthResponse;
import com.cepsandik.userservice.dtos.responses.UserResponse;
import com.cepsandik.userservice.models.User;
import com.cepsandik.userservice.security.ClientInfoResolver;
import com.cepsandik.userservice.security.JwtService;
import com.cepsandik.userservice.service.AuditService;
import lombok.RequiredArgsConstructor;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.AfterThrowing;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Aspect
@Component
@RequiredArgsConstructor
public class AuditLoggingAspect {

    private final AuditService auditService;
    private final ClientInfoResolver clientInfoResolver;
    private final JwtService jwtService;

    @AfterReturning(pointcut = "@annotation(logAudit)", returning = "result")
    public void logSuccess(JoinPoint joinPoint, LogAudit logAudit, Object result) {
        String ip = clientInfoResolver.getClientIp();

        // ID'yi bulmak için akıllı yöntem
        UUID userId = resolveUserId(result);

        String action = logAudit.action() + "_SUCCESS";
        String details = "İşlem başarıyla tamamlandı.";

        auditService.log(userId, action, details, ip);
    }

    @AfterThrowing(pointcut = "@annotation(logAudit)", throwing = "ex")
    public void logFailure(JoinPoint joinPoint, LogAudit logAudit, Exception ex) {
        String ip = clientInfoResolver.getClientIp();

        UUID userId = resolveUserId(null);

        String action = logAudit.action() + "_FAIL";
        String details = "Hata: " + ex.getMessage();

        auditService.log(userId, action, details, ip);
    }

    private UUID resolveUserId(Object result) {

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof User) {
            return ((User) auth.getPrincipal()).getId();
        }
        if (result instanceof UserResponse) {
            return ((UserResponse) result).getId();
        }
        if (result instanceof AuthResponse authResponse) {
            // 2FA durumunda accessToken null olabilir
            if (authResponse.getAccessToken() != null) {
                return jwtService.extractSubject(authResponse.getAccessToken());
            }
            return null;
        }

        return null;
    }
}