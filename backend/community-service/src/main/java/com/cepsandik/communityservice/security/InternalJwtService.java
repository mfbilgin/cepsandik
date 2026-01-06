package com.cepsandik.communityservice.security;

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
}
