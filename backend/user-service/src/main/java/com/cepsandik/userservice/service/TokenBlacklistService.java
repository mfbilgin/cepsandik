package com.cepsandik.userservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * Token Blacklist Service - Redis kullanarak logout edilen token'ları geçersiz
 * kılar
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TokenBlacklistService {

    private final StringRedisTemplate redisTemplate;

    private static final String BLACKLIST_PREFIX = "token:blacklist:";

    /**
     * Token'ı blacklist'e ekler
     * 
     * @param token             JWT token
     * @param expirationSeconds Token'ın kalan expire süresi (saniye)
     */
    public void blacklistToken(String token, long expirationSeconds) {
        if (token == null || token.isEmpty()) {
            return;
        }

        String key = BLACKLIST_PREFIX + token;
        // Token'ı Redis'e ekle, TTL = token'ın kalan expire süresi
        redisTemplate.opsForValue().set(key, "blacklisted", Duration.ofSeconds(expirationSeconds));
        log.info("Token blacklisted, expires in {} seconds", expirationSeconds);
    }

    /**
     * Token blacklist'te mi kontrol eder
     * 
     * @param token JWT token
     * @return true ise token geçersiz (blacklisted)
     */
    public boolean isBlacklisted(String token) {
        if (token == null || token.isEmpty()) {
            return false;
        }

        String key = BLACKLIST_PREFIX + token;
        return Boolean.TRUE.equals(redisTemplate.hasKey(key));
    }

    /**
     * Token'ın kalan süresini hesaplar (saniye)
     */
    public long calculateRemainingSeconds(long expirationTimestamp) {
        long now = System.currentTimeMillis() / 1000;
        long remaining = expirationTimestamp - now;
        return Math.max(remaining, 0);
    }
}
