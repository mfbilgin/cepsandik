package com.cepsandik.userservice.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Component
public class ClientInfoResolver {

    public String getClientIp() {
        try {
            ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attributes == null) return "UNKNOWN";

            HttpServletRequest request = attributes.getRequest();

            String remoteAddr = request.getHeader("X-FORWARDED-FOR");

            if (remoteAddr == null || remoteAddr.isEmpty()) {
                remoteAddr = request.getRemoteAddr();
            }

            return remoteAddr.split(",")[0].trim();
        } catch (Exception e) {
            return "UNKNOWN";
        }
    }
}