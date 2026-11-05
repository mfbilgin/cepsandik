package com.cepsandik.userservice.service.integrity;

import com.cepsandik.userservice.exceptions.ApiException;
import com.fasterxml.jackson.databind.JsonNode;
import com.google.auth.oauth2.GoogleCredentials;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Base64;
import java.util.List;
import java.util.Map;

/**
 * Play Integrity token'ını Google sunucusunda çözer
 * (POST playintegrity.googleapis.com/v1/{package}:decodeIntegrityToken).
 *
 * Kimlik bilgisi: Play Console'a bağlı Google Cloud projesindeki bir servis
 * hesabının JSON anahtarı, base64 olarak PLAY_INTEGRITY_SA_JSON_BASE64 ortam
 * değişkeninde verilir. Dosya imaja girmez.
 */
@Component
@Slf4j
public class PlayIntegrityClient {

    private static final String SCOPE = "https://www.googleapis.com/auth/playintegrity";
    private static final String ENDPOINT = "https://playintegrity.googleapis.com/v1/{package}:decodeIntegrityToken";

    private final String packageName;
    private final String credentialsBase64;
    private final RestClient restClient;
    private volatile GoogleCredentials credentials;

    public PlayIntegrityClient(
            @Value("${app.integrity.package-name:com.cepsandik.mobile}") String packageName,
            @Value("${app.integrity.service-account-json-base64:}") String credentialsBase64) {
        this.packageName = packageName;
        this.credentialsBase64 = credentialsBase64;
        this.restClient = RestClient.create();
    }

    /** @return tokenPayloadExternal düğümü */
    public JsonNode decode(String integrityToken) {
        try {
            JsonNode body = restClient.post()
                    .uri(ENDPOINT, packageName)
                    .header("Authorization", "Bearer " + accessToken())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("integrity_token", integrityToken))
                    .retrieve()
                    .body(JsonNode.class);
            JsonNode payload = body == null ? null : body.get("tokenPayloadExternal");
            if (payload == null) {
                throw new ApiException(HttpStatus.BAD_GATEWAY, "Bütünlük yanıtı okunamadı");
            }
            return payload;
        } catch (RestClientException e) {
            // Geçersiz/bozuk token Google'dan 400 döner; ağ hataları da buraya düşer.
            log.warn("Play Integrity decode failed: {}", e.getMessage());
            throw new ApiException(HttpStatus.BAD_GATEWAY, "Bütünlük doğrulama servisine ulaşılamadı");
        }
    }

    private String accessToken() {
        try {
            GoogleCredentials creds = credentials;
            if (creds == null) {
                synchronized (this) {
                    if (credentials == null) {
                        if (credentialsBase64 == null || credentialsBase64.isBlank()) {
                            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE,
                                    "Bütünlük doğrulaması yapılandırılmamış");
                        }
                        byte[] json = Base64.getDecoder().decode(credentialsBase64.trim());
                        credentials = GoogleCredentials.fromStream(new ByteArrayInputStream(json))
                                .createScoped(List.of(SCOPE));
                    }
                    creds = credentials;
                }
            }
            creds.refreshIfExpired();
            return creds.getAccessToken().getTokenValue();
        } catch (IOException | IllegalArgumentException e) {
            log.error("Play Integrity credentials error: {}", e.getMessage());
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "Bütünlük doğrulaması yapılandırılamadı");
        }
    }
}
