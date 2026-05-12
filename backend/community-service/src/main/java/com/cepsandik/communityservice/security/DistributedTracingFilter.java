package com.cepsandik.communityservice.security;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class DistributedTracingFilter extends OncePerRequestFilter {

    private static final String CORRELATION_ID_HEADER = "X-Correlation-ID";
    private static final String APP_VERSION_HEADER = "X-App-Version";
    private static final String PLATFORM_HEADER = "X-Platform";
    private static final String DEVICE_ID_HEADER = "X-Device-ID";
    private static final String USER_ID_HEADER = "X-User-Id";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        
        String correlationId = request.getHeader(CORRELATION_ID_HEADER);
        if (correlationId == null || correlationId.isEmpty()) {
            correlationId = UUID.randomUUID().toString();
        }

        try {
            MDC.put("request_id", correlationId);
            populateMdc(APP_VERSION_HEADER, "app_version", request);
            populateMdc(PLATFORM_HEADER, "platform", request);
            populateMdc(DEVICE_ID_HEADER, "device_id", request);
            populateMdc(USER_ID_HEADER, "user_id", request);
            
            MDC.put("endpoint", request.getRequestURI());
            MDC.put("method", request.getMethod());

            response.setHeader(CORRELATION_ID_HEADER, correlationId);
            
            filterChain.doFilter(request, response);
        } finally {
            MDC.clear();
        }
    }

    private void populateMdc(String headerName, String mdcKey, HttpServletRequest request) {
        String value = request.getHeader(headerName);
        if (value != null && !value.isEmpty()) {
            MDC.put(mdcKey, value);
        }
    }
}
