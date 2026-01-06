package com.cepsandik.userservice.security;

import com.cepsandik.userservice.common.MessageConstants;
import com.cepsandik.userservice.dtos.responses.ApiResponse;
import com.cepsandik.userservice.service.RateLimitingService;
import com.cepsandik.userservice.service.AuditService;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.regex.Pattern;

@Component
@RequiredArgsConstructor
public class RateLimitFilter extends OncePerRequestFilter {

    //private final ClientInfoResolver clientInfoResolver;
    private final RateLimitingService rateLimitingService;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;
    private static final Pattern AUTH_PATTERN = Pattern.compile("^/api/v\\d+/auth.*");

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain)
            throws ServletException, IOException {

        String ip = request.getHeader("X-FORWARDED-FOR");
        if (ip == null || ip.isEmpty()) {
            ip = request.getRemoteAddr();
        }
        ip = ip.split(",")[0].trim();


        String path = request.getRequestURI();
        boolean isAuthEndpoint = AUTH_PATTERN.matcher(path).matches();
        String key = isAuthEndpoint ? "auth_limit:" + ip : "general_limit:" + ip;

        Bucket bucket = rateLimitingService.resolveBucket(key, isAuthEndpoint);

        ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);

        if (probe.isConsumed()) {
            response.addHeader("X-Rate-Limit-Remaining", String.valueOf(probe.getRemainingTokens()));
            filterChain.doFilter(request, response);
        } else {

            auditService.log(null, "RATE_LIMIT_EXCEEDED", "IP engellendi: " + path, ip);

            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setContentType("application/json");
            response.setCharacterEncoding("UTF-8");
            long waitForRefill = probe.getNanosToWaitForRefill() / 1_000_000_000;
            response.addHeader("Retry-After", String.valueOf(waitForRefill));

            ApiResponse<Void> errorResponse = ApiResponse.fail(MessageConstants.RATE_LIMIT_EXCEEDED);

            String jsonResponse = objectMapper.writeValueAsString(errorResponse);
            response.getWriter().write(jsonResponse);
        }
    }
}