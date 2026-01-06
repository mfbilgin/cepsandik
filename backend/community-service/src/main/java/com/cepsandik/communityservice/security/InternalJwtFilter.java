package com.cepsandik.communityservice.security;

import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class InternalJwtFilter extends OncePerRequestFilter {

    private final InternalJwtService jwtService;

    // Exclude edilecek path'ler
    private static final List<String> EXCLUDED_PATHS = Arrays.asList(
            "/actuator",
            "/swagger-ui",
            "/api-docs",
            "/v3/api-docs");

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return EXCLUDED_PATHS.stream().anyMatch(path::startsWith);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {

        String internalToken = request.getHeader("X-Internal-Auth");

        log.debug("=== InternalJwtFilter Debug ===");
        log.debug("X-Internal-Auth header: {}", internalToken != null ? "PRESENT" : "MISSING");
        log.debug("Request URI: {}", request.getRequestURI());

        if (internalToken == null || internalToken.isEmpty()) {
            log.error("X-Internal-Auth header eksik");
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"success\":false,\"message\":\"Yetkisiz: Internal token eksik\"}");
            return;
        }

        try {
            // Token doğrulama
            String issuer = jwtService.extractIssuer(internalToken);

            // API Gateway'den gelmiş olmalı
            if (!"api-gateway".equals(issuer)) {
                log.error("Geçersiz issuer: {}", issuer);
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                response.setContentType("application/json;charset=UTF-8");
                response.getWriter().write("{\"success\":false,\"message\":\"Yetkisiz: Geçersiz token kaynağı\"}");
                return;
            }

            // User ID'yi çıkar ve request attribute olarak ayarla
            String userId = jwtService.extractUserId(internalToken);
            request.setAttribute("userId", userId);

            log.debug("İstek doğrulandı, kullanıcı: {}", userId);

        } catch (JwtException e) {
            log.error("JWT doğrulama hatası: {}", e.getMessage());
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"success\":false,\"message\":\"Yetkisiz: Geçersiz token\"}");
            return;
        }

        filterChain.doFilter(request, response);
    }
}
