package com.cepsandik.userservice.service.integrity;

import com.cepsandik.userservice.exceptions.ApiException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.http.HttpStatus;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("PlayIntegrityService Unit Tests")
class PlayIntegrityServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-06T10:00:00Z");

    @Mock
    private StringRedisTemplate redis;
    @Mock
    private ValueOperations<String, String> valueOps;
    @Mock
    private PlayIntegrityClient client;

    private PlayIntegrityService service(boolean enabled) {
        return new PlayIntegrityService(redis, client, enabled,
                new IntegrityVerdictEvaluator("com.cepsandik.mobile", true, Duration.ofMinutes(5)),
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    @DisplayName("Kapalıyken challenge enabled=false döner ve Redis'e yazmaz")
    void disabledChallenge() {
        var res = service(false).issueChallenge();

        assertThat(res.enabled()).isFalse();
        verifyNoInteractions(redis);
    }

    @Test
    @DisplayName("Açıkken challenge Redis'e TTL ile yazılır")
    void enabledChallengeStoredWithTtl() {
        when(redis.opsForValue()).thenReturn(valueOps);

        var res = service(true).issueChallenge();

        assertThat(res.enabled()).isTrue();
        assertThat(res.nonce()).hasSize(43);
        verify(valueOps).set(eq(PlayIntegrityService.NONCE_PREFIX + res.nonce()), eq("1"), eq(Duration.ofMinutes(5)));
    }

    @Test
    @DisplayName("Bilinmeyen veya kullanılmış challenge reddedilir, Google'a gidilmez")
    void unknownNonceRejected() {
        when(redis.delete(anyString())).thenReturn(false);

        assertThatThrownBy(() -> service(true).verify("used", "token"))
                .isInstanceOf(ApiException.class)
                .extracting("status").isEqualTo(HttpStatus.BAD_REQUEST);
        verifyNoInteractions(client);
    }

    @Test
    @DisplayName("Geçerli challenge tüketilir ve karar döner")
    void validNonceConsumedAndEvaluated() throws Exception {
        when(redis.delete(PlayIntegrityService.NONCE_PREFIX + "n1")).thenReturn(true);
        when(client.decode("tok")).thenReturn(new ObjectMapper().readTree("""
                {"requestDetails": {"requestPackageName": "com.cepsandik.mobile", "requestHash": "n1",
                                    "timestampMillis": "%d"},
                 "appIntegrity": {"appRecognitionVerdict": "PLAY_RECOGNIZED"},
                 "deviceIntegrity": {"deviceRecognitionVerdict": ["MEETS_DEVICE_INTEGRITY"]}}
                """.formatted(NOW.toEpochMilli())));

        var res = service(true).verify("n1", "tok");

        assertThat(res.passed()).isTrue();
    }
}
