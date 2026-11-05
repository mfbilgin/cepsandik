package com.cepsandik.userservice.service.integrity;

import com.cepsandik.userservice.dtos.responses.IntegrityChallengeResponse;
import com.cepsandik.userservice.dtos.responses.IntegrityVerdictResponse;
import com.cepsandik.userservice.exceptions.ApiException;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.util.Base64;

/**
 * Cihaz bütünlüğü doğrulaması (Google Play Integrity API).
 *
 * 1. issueChallenge: 256-bit rastgele, tek kullanımlık challenge üretir ve
 *    Redis'e TTL ile yazar. İstemci bunu Standard request'in requestHash'i
 *    olarak kullanır → token bu isteğe bağlanır (replay engellenir).
 * 2. verify: challenge'ı Redis'ten atomik olarak TÜKETİR, token'ı Google'da
 *    çözer ve IntegrityVerdictEvaluator ile karar verir.
 *
 * app.integrity.enabled=false iken (yerel/UAT) istemciye enabled=false döner
 * ve kontrol atlanır.
 */
@Service
@Slf4j
public class PlayIntegrityService {

    static final String NONCE_PREFIX = "integrity:nonce:";
    private static final Duration NONCE_TTL = Duration.ofMinutes(5);

    private final StringRedisTemplate redis;
    private final PlayIntegrityClient client;
    private final IntegrityVerdictEvaluator evaluator;
    private final boolean enabled;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    @Autowired
    public PlayIntegrityService(
            StringRedisTemplate redis,
            PlayIntegrityClient client,
            @Value("${app.integrity.enabled:false}") boolean enabled,
            @Value("${app.integrity.package-name:com.cepsandik.mobile}") String packageName,
            @Value("${app.integrity.require-play-recognized:true}") boolean requirePlayRecognized) {
        this(redis, client, enabled,
                new IntegrityVerdictEvaluator(packageName, requirePlayRecognized, NONCE_TTL),
                Clock.systemUTC());
    }

    PlayIntegrityService(StringRedisTemplate redis, PlayIntegrityClient client, boolean enabled,
                         IntegrityVerdictEvaluator evaluator, Clock clock) {
        this.redis = redis;
        this.client = client;
        this.enabled = enabled;
        this.evaluator = evaluator;
        this.clock = clock;
    }

    public IntegrityChallengeResponse issueChallenge() {
        if (!enabled) {
            return new IntegrityChallengeResponse(false, null, 0);
        }
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String nonce = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        redis.opsForValue().set(NONCE_PREFIX + nonce, "1", NONCE_TTL);
        return new IntegrityChallengeResponse(true, nonce, NONCE_TTL.toSeconds());
    }

    public IntegrityVerdictResponse verify(String nonce, String token) {
        if (!enabled) {
            throw new ApiException(HttpStatus.CONFLICT, "Bütünlük doğrulaması bu ortamda kapalı");
        }
        // Tek kullanımlık: silme başarılıysa challenge geçerliydi.
        if (!Boolean.TRUE.equals(redis.delete(NONCE_PREFIX + nonce))) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Geçersiz veya süresi dolmuş challenge");
        }

        JsonNode payload = client.decode(token);
        IntegrityVerdictEvaluator.Result result = evaluator.evaluate(payload, nonce, clock.instant());
        if (!result.passed()) {
            log.warn("Device integrity check failed: {}", result.reasons());
        }
        return new IntegrityVerdictResponse(result.passed(), result.reasons());
    }
}
