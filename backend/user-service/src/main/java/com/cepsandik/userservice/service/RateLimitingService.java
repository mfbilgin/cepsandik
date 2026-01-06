package com.cepsandik.userservice.service;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.BucketConfiguration;
import io.github.bucket4j.redis.lettuce.cas.LettuceBasedProxyManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.function.Supplier;

/**
 * Business-specific rate limiting service.
 * 
 * Gateway handles DDoS protection (IP-based).
 * This service handles user-specific business rules.
 */
@Service
@RequiredArgsConstructor
public class RateLimitingService {

    private final LettuceBasedProxyManager<String> proxyManager;

    /**
     * Rate limit configurations for different operations
     */
    public enum RateLimitType {
        // Parola sıfırlama: 3 istek/saat (abuse prevention)
        PASSWORD_RESET(3, Duration.ofHours(1)),

        // Email doğrulama yeniden gönderme: 5 istek/saat
        EMAIL_VERIFICATION_RESEND(5, Duration.ofHours(1)),

        // Profil güncelleme: 10 istek/saat
        PROFILE_UPDATE(10, Duration.ofHours(1)),

        // Genel endpoint: 100 istek/dakika
        GENERAL(100, Duration.ofMinutes(1));

        private final int capacity;
        private final Duration refillDuration;

        RateLimitType(int capacity, Duration refillDuration) {
            this.capacity = capacity;
            this.refillDuration = refillDuration;
        }
    }

    /**
     * Belirli bir kullanıcı ve işlem tipi için bucket oluşturur.
     * 
     * @param userId Kullanıcı ID
     * @param type   Rate limit tipi
     * @return Bucket
     */
    public Bucket resolveBucket(String userId, RateLimitType type) {
        String key = "ratelimit:" + type.name().toLowerCase() + ":" + userId;
        return proxyManager.builder().build(key, getConfigSupplier(type));
    }

    /**
     * Eski API uyumluluğu için (deprecated)
     */
    @Deprecated
    public Bucket resolveBucket(String key, boolean isAuthEndpoint) {
        RateLimitType type = isAuthEndpoint ? RateLimitType.GENERAL : RateLimitType.GENERAL;
        return proxyManager.builder().build(key, getConfigSupplier(type));
    }

    private Supplier<BucketConfiguration> getConfigSupplier(RateLimitType type) {
        return () -> BucketConfiguration.builder()
                .addLimit(Bandwidth.builder()
                        .capacity(type.capacity)
                        .refillGreedy(type.capacity, type.refillDuration)
                        .build())
                .build();
    }
}
