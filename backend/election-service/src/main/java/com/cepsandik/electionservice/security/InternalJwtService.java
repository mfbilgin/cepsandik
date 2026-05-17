package com.cepsandik.electionservice.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;

@Service
public class InternalJwtService {

    private final SecretKey secretKey;

    public InternalJwtService(@Value("${jwt.internal.secret}") String secret) {
        this.secretKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    public Claims validateToken(String token) {
        return Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public String extractUserId(String token) {
        Claims claims = validateToken(token);
        Object uid = claims.get("uid");
        if (uid instanceof String) {
            return (String) uid;
        } else if (uid instanceof Number) {
            return uid.toString();
        }
        throw new IllegalArgumentException("Invalid uid claim type: " + uid.getClass());
    }

    public String extractIssuer(String token) {
        Claims claims = validateToken(token);
        return claims.getIssuer();
    }

    /**
     * Service-to-service internal token üretir. Gateway'in ürettiği token ile
     * aynı sözleşme: HS256 + iss="api-gateway" + uid claim. Alıcı servislerin
     * InternalJwtFilter'ı bunu kabul eder. (election→community/user iç çağrıları
     * için; gateway sadece client→service yolunu kapsıyor — bu yol distributed
     * guardian seçimi gelene kadar dev-bypass arkasında dormant'tı.)
     */
    public String generateServiceToken() {
        long now = System.currentTimeMillis();
        return Jwts.builder()
                .issuer("api-gateway")
                .claim("uid", "election-service")
                .claim("roles", java.util.List.of("SERVICE"))
                .claim("scope", java.util.List.of("community:read", "user:read"))
                .issuedAt(new java.util.Date(now))
                .expiration(new java.util.Date(now + 60_000))
                .signWith(secretKey)
                .compact();
    }
}
