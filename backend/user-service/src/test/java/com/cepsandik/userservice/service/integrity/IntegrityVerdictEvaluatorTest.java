package com.cepsandik.userservice.service.integrity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static com.cepsandik.userservice.service.integrity.IntegrityVerdictEvaluator.*;
import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("IntegrityVerdictEvaluator Unit Tests")
class IntegrityVerdictEvaluatorTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String PKG = "com.cepsandik.mobile";
    private static final String NONCE = "abc123";
    private static final Instant NOW = Instant.parse("2026-11-06T10:00:00Z");

    private final IntegrityVerdictEvaluator evaluator =
            new IntegrityVerdictEvaluator(PKG, true, Duration.ofMinutes(5));

    private static JsonNode payload(String pkg, String hash, long ts, String appVerdict, String deviceVerdicts)
            throws Exception {
        return MAPPER.readTree("""
                {
                  "requestDetails": {"requestPackageName": "%s", "requestHash": "%s", "timestampMillis": "%d"},
                  "appIntegrity": {"appRecognitionVerdict": "%s"},
                  "deviceIntegrity": {"deviceRecognitionVerdict": [%s]}
                }
                """.formatted(pkg, hash, ts, appVerdict, deviceVerdicts));
    }

    @Test
    @DisplayName("Sağlam cihaz ve Play'den yüklenmiş uygulama geçer")
    void genuineDevicePasses() throws Exception {
        var result = evaluator.evaluate(
                payload(PKG, NONCE, NOW.toEpochMilli(), "PLAY_RECOGNIZED", "\"MEETS_DEVICE_INTEGRITY\""),
                NONCE, NOW);

        assertThat(result.passed()).isTrue();
        assertThat(result.reasons()).isEmpty();
    }

    @Test
    @DisplayName("Root'lu cihaz (yalnızca MEETS_BASIC_INTEGRITY) reddedilir")
    void rootedDeviceFails() throws Exception {
        var result = evaluator.evaluate(
                payload(PKG, NONCE, NOW.toEpochMilli(), "PLAY_RECOGNIZED", "\"MEETS_BASIC_INTEGRITY\""),
                NONCE, NOW);

        assertThat(result.passed()).isFalse();
        assertThat(result.reasons()).containsExactly(DEVICE_INTEGRITY_NOT_MET);
    }

    @Test
    @DisplayName("Boş cihaz kararı (emülatör / kilidi açık bootloader) reddedilir")
    void emptyDeviceVerdictFails() throws Exception {
        var result = evaluator.evaluate(
                payload(PKG, NONCE, NOW.toEpochMilli(), "PLAY_RECOGNIZED", ""), NONCE, NOW);

        assertThat(result.reasons()).containsExactly(DEVICE_INTEGRITY_NOT_MET);
    }

    @Test
    @DisplayName("Başka bir challenge için alınmış token (replay) reddedilir")
    void replayedTokenFails() throws Exception {
        var result = evaluator.evaluate(
                payload(PKG, "other-nonce", NOW.toEpochMilli(), "PLAY_RECOGNIZED", "\"MEETS_DEVICE_INTEGRITY\""),
                NONCE, NOW);

        assertThat(result.reasons()).containsExactly(REQUEST_HASH_MISMATCH);
    }

    @Test
    @DisplayName("Değiştirilmiş / yeniden paketlenmiş uygulama reddedilir")
    void tamperedAppFails() throws Exception {
        var result = evaluator.evaluate(
                payload("com.evil.clone", NONCE, NOW.toEpochMilli(), "UNRECOGNIZED_VERSION", "\"MEETS_DEVICE_INTEGRITY\""),
                NONCE, NOW);

        assertThat(result.reasons()).containsExactlyInAnyOrder(PACKAGE_MISMATCH, APP_NOT_PLAY_RECOGNIZED);
    }

    @Test
    @DisplayName("Eski token reddedilir")
    void staleTokenFails() throws Exception {
        long old = NOW.minus(Duration.ofMinutes(10)).toEpochMilli();
        var result = evaluator.evaluate(
                payload(PKG, NONCE, old, "PLAY_RECOGNIZED", "\"MEETS_DEVICE_INTEGRITY\""), NONCE, NOW);

        assertThat(result.reasons()).containsExactly(TOKEN_STALE);
    }

    @Test
    @DisplayName("PLAY_RECOGNIZED şartı kapatılınca APK ile kurulan uygulama geçer")
    void sideloadAllowedWhenConfigured() throws Exception {
        var lenient = new IntegrityVerdictEvaluator(PKG, false, Duration.ofMinutes(5));
        var result = lenient.evaluate(
                payload(PKG, NONCE, NOW.toEpochMilli(), "UNRECOGNIZED_VERSION", "\"MEETS_DEVICE_INTEGRITY\""),
                NONCE, NOW);

        assertThat(result.passed()).isTrue();
    }
}
