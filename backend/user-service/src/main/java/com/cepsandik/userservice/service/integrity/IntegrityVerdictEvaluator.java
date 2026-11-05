package com.cepsandik.userservice.service.integrity;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Google Play Integrity "tokenPayloadExternal" çıktısını değerlendirir.
 *
 * Saf (yan etkisiz) sınıf: ağ, Redis veya Spring bağımlılığı yoktur; birim
 * testleriyle doğrudan doğrulanır.
 *
 * Kontroller:
 *  1. requestDetails.requestPackageName == beklenen paket adı
 *  2. requestDetails.requestHash == sunucunun verdiği tek kullanımlık challenge
 *  3. requestDetails.timestampMillis taze (maxAge içinde, gelecekte değil)
 *  4. appIntegrity.appRecognitionVerdict == PLAY_RECOGNIZED (opsiyonel)
 *  5. deviceIntegrity.deviceRecognitionVerdict MEETS_DEVICE_INTEGRITY içerir
 *     → root'lu cihaz, kilidi açık bootloader, tanınmayan ROM veya emülatör
 *       bu etiketi alamaz.
 */
public final class IntegrityVerdictEvaluator {

    public static final String PACKAGE_MISMATCH = "PACKAGE_MISMATCH";
    public static final String REQUEST_HASH_MISMATCH = "REQUEST_HASH_MISMATCH";
    public static final String TOKEN_STALE = "TOKEN_STALE";
    public static final String APP_NOT_PLAY_RECOGNIZED = "APP_NOT_PLAY_RECOGNIZED";
    public static final String DEVICE_INTEGRITY_NOT_MET = "DEVICE_INTEGRITY_NOT_MET";

    private static final Duration CLOCK_SKEW = Duration.ofSeconds(30);

    private final String expectedPackageName;
    private final boolean requirePlayRecognized;
    private final Duration maxAge;

    public IntegrityVerdictEvaluator(String expectedPackageName, boolean requirePlayRecognized, Duration maxAge) {
        this.expectedPackageName = expectedPackageName;
        this.requirePlayRecognized = requirePlayRecognized;
        this.maxAge = maxAge;
    }

    public Result evaluate(JsonNode payload, String expectedRequestHash, Instant now) {
        List<String> reasons = new ArrayList<>();
        JsonNode request = payload.path("requestDetails");

        if (!expectedPackageName.equals(request.path("requestPackageName").asText(null))) {
            reasons.add(PACKAGE_MISMATCH);
        }
        if (!expectedRequestHash.equals(request.path("requestHash").asText(null))) {
            reasons.add(REQUEST_HASH_MISMATCH);
        }

        long ts = request.path("timestampMillis").asLong(0);
        Instant issuedAt = Instant.ofEpochMilli(ts);
        if (ts <= 0 || issuedAt.isBefore(now.minus(maxAge)) || issuedAt.isAfter(now.plus(CLOCK_SKEW))) {
            reasons.add(TOKEN_STALE);
        }

        if (requirePlayRecognized
                && !"PLAY_RECOGNIZED".equals(payload.path("appIntegrity").path("appRecognitionVerdict").asText(null))) {
            reasons.add(APP_NOT_PLAY_RECOGNIZED);
        }

        if (!containsText(payload.path("deviceIntegrity").path("deviceRecognitionVerdict"), "MEETS_DEVICE_INTEGRITY")) {
            reasons.add(DEVICE_INTEGRITY_NOT_MET);
        }

        return new Result(reasons.isEmpty(), List.copyOf(reasons));
    }

    private static boolean containsText(JsonNode array, String value) {
        if (!array.isArray()) {
            return false;
        }
        for (JsonNode item : array) {
            if (value.equals(item.asText())) {
                return true;
            }
        }
        return false;
    }

    public record Result(boolean passed, List<String> reasons) {
    }
}
