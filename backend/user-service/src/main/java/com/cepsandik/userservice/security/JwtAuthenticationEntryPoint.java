package com.cepsandik.userservice.security;
import com.cepsandik.userservice.common.MessageConstants;
import com.cepsandik.userservice.dtos.responses.ApiResponse;
import com.cepsandik.userservice.service.AuditService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
@RequiredArgsConstructor
public class JwtAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final AuditService auditService;
    private final ClientInfoResolver clientInfoResolver;
    private final ObjectMapper objectMapper;

    @Override
    public void commence(HttpServletRequest request,
                         HttpServletResponse response,
                         AuthenticationException authException) throws IOException {

        String ip = clientInfoResolver.getClientIp();
        String path = request.getRequestURI();

        auditService.log(
                null,
                "UNAUTHENTICATED_ACCESS",
                "Erişim reddedildi. Yol: " + path + " - Hata: " + authException.getMessage(),
                ip
        );

        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);

        ApiResponse<Void> errorResponse = ApiResponse.fail(MessageConstants.UNAUTHENTICATED_REQUEST);

        String jsonResponse = objectMapper.writeValueAsString(errorResponse);
        response.getWriter().write(jsonResponse);
    }
}