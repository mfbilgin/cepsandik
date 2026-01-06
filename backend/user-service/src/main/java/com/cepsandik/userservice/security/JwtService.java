package com.cepsandik.userservice.security;

import com.cepsandik.userservice.models.User;
import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.Map;
import java.util.UUID;

@Service
public class JwtService {
    private final byte[] key;
    private final long accessTtlSeconds;

    public JwtService(
            @Value("${app.jwt.secret}") String secret,
            @Value("${app.jwt.access-ttl-seconds:900}") long accessTtlSeconds) {
        this.key = secret.getBytes(StandardCharsets.UTF_8);
        this.accessTtlSeconds = accessTtlSeconds;
    }

    public Map<String, Object> generateAccessTokenWithClaims(User user) {
        Instant now = Instant.now();
        Date expiration = Date.from(now.plusSeconds(accessTtlSeconds));
        String token = Jwts.builder()
                .issuer("https://api.cepsandik.com")
                .subject(user.getId().toString())
                .audience().add("web-app").add("mobile-app")
                .and()
                .claim("email", user.getEmail())
                .claim("platformRole", user.getPlatformRole().name())
                .issuedAt(Date.from(now))
                .expiration(expiration)
                .signWith(Keys.hmacShaKeyFor(key), Jwts.SIG.HS256)
                .compact();
        return Map.of("token", token, "expiration", expiration.getTime());
    }

    public UUID extractSubject(String token) {
        return UUID.fromString(
                Jwts.parser()
                        .verifyWith(Keys.hmacShaKeyFor(key))
                        .requireIssuer("https://api.cepsandik.com")
                        .build()
                        .parseSignedClaims(token)
                        .getPayload()
                        .getSubject());
    }

    public String extractEmail(String token) {
        return Jwts.parser()
                .verifyWith(Keys.hmacShaKeyFor(key))
                .build()
                .parseSignedClaims(token)
                .getPayload()
                .get("email", String.class);
    }

    /**
     * Token'ın expiration timestamp'ini çıkarır (epoch saniye)
     */
    public long extractExpiration(String token) {
        return Jwts.parser()
                .verifyWith(Keys.hmacShaKeyFor(key))
                .build()
                .parseSignedClaims(token)
                .getPayload()
                .getExpiration()
                .getTime() / 1000; // milliseconds to seconds
    }

    /**
     * 2FA için geçici token oluşturur (5 dakika geçerli)
     */
    public String generateTempToken(User user) {
        Instant now = Instant.now();
        return Jwts.builder()
                .issuer("https://api.cepsandik.com")
                .subject(user.getId().toString())
                .claim("purpose", "2fa")
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(300))) // 5 dakika
                .signWith(Keys.hmacShaKeyFor(key), Jwts.SIG.HS256)
                .compact();
    }

    /**
     * 2FA geçici token'ını doğrular ve user id döner
     */
    public UUID validateTempTokenAndGetUserId(String token) {
        try {
            var claims = Jwts.parser()
                    .verifyWith(Keys.hmacShaKeyFor(key))
                    .requireIssuer("https://api.cepsandik.com")
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();

            // purpose claim kontrolü
            String purpose = claims.get("purpose", String.class);
            if (!"2fa".equals(purpose)) {
                return null;
            }

            return UUID.fromString(claims.getSubject());
        } catch (JwtException e) {
            return null;
        }
    }
}
