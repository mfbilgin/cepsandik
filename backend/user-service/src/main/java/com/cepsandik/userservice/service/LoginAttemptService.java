package com.cepsandik.userservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.TimeUnit;

/**
 * Başarısız giriş denemelerini takip eden ve geçici hesap engelleme sağlayan
 * servis.
 * Brute-force saldırılarına karşı koruma sağlar.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class LoginAttemptService {

    private final RedisTemplate<String, Object> redisTemplate;

    private static final int MAX_ATTEMPTS = 5;
    private static final int LOCKOUT_MINUTES = 15;
    private static final int ATTEMPT_EXPIRY_MINUTES = 30;

    private static final String ATTEMPTS_PREFIX = "login:attempts:";
    private static final String LOCKOUT_PREFIX = "login:lockout:";

    /**
     * Başarısız giriş denemesini kaydet.
     * Maksimum deneme sayısına ulaşılırsa hesabı engelle.
     */
    public void recordFailedAttempt(String email) {
        String attemptsKey = ATTEMPTS_PREFIX + email.toLowerCase();
        String lockoutKey = LOCKOUT_PREFIX + email.toLowerCase();

        Long attempts = redisTemplate.opsForValue().increment(attemptsKey);

        if (attempts != null && attempts == 1) {
            // İlk deneme, TTL ayarla
            redisTemplate.expire(attemptsKey, ATTEMPT_EXPIRY_MINUTES, TimeUnit.MINUTES);
        }

        if (attempts != null && attempts >= MAX_ATTEMPTS) {
            // Hesabı engelle
            long lockoutEndTime = Instant.now().plusSeconds(LOCKOUT_MINUTES * 60L).toEpochMilli();
            redisTemplate.opsForValue().set(lockoutKey, lockoutEndTime, Duration.ofMinutes(LOCKOUT_MINUTES));
            log.warn("Hesap geçici olarak engellendi: email={}, attempts={}", email, attempts);
        }

        log.info("Başarısız giriş denemesi kaydedildi: email={}, attempts={}", email, attempts);
    }

    /**
     * Hesabın engellenmiş olup olmadığını kontrol et.
     */
    public boolean isBlocked(String email) {
        String lockoutKey = LOCKOUT_PREFIX + email.toLowerCase();
        Object lockoutEndTime = redisTemplate.opsForValue().get(lockoutKey);

        if (lockoutEndTime == null) {
            return false;
        }

        long endTime = ((Number) lockoutEndTime).longValue();
        return Instant.now().toEpochMilli() < endTime;
    }

    /**
     * Başarılı giriş sonrası denemeleri sıfırla.
     */
    public void clearAttempts(String email) {
        String attemptsKey = ATTEMPTS_PREFIX + email.toLowerCase();
        String lockoutKey = LOCKOUT_PREFIX + email.toLowerCase();

        redisTemplate.delete(attemptsKey);
        redisTemplate.delete(lockoutKey);

        log.info("Başarısız giriş denemeleri sıfırlandı: email={}", email);
    }

    /**
     * Kalan deneme sayısını döndür.
     */
    public int getRemainingAttempts(String email) {
        String attemptsKey = ATTEMPTS_PREFIX + email.toLowerCase();
        Object attempts = redisTemplate.opsForValue().get(attemptsKey);

        if (attempts == null) {
            return MAX_ATTEMPTS;
        }

        int currentAttempts = ((Number) attempts).intValue();
        return Math.max(0, MAX_ATTEMPTS - currentAttempts);
    }

    /**
     * Engel bitiş zamanını dakika olarak döndür.
     */
    public long getRemainingLockoutMinutes(String email) {
        String lockoutKey = LOCKOUT_PREFIX + email.toLowerCase();
        Object lockoutEndTime = redisTemplate.opsForValue().get(lockoutKey);

        if (lockoutEndTime == null) {
            return 0;
        }

        long endTime = ((Number) lockoutEndTime).longValue();
        long remainingMs = endTime - Instant.now().toEpochMilli();

        return Math.max(0, (remainingMs / 1000 / 60) + 1);
    }
}
