package com.cepsandik.electionservice.client;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class BulletinBoardClient {

    private final RestTemplate restTemplate;

    @Value("${app.bulletin-board.url:http://localhost:8085}")
    private String bulletinBoardUrl;

    public void appendRecord(String electionId, String recordType, String trackingCode, String ballotHash, String payload) {
        appendRecord(electionId, recordType, trackingCode, ballotHash, payload, null);
    }

    /**
     * Bulletin-board'a kayıt ekler. {@code idempotencyKey} verilirse aynı
     * anahtarla tekrar gönderim no-op'tur (outbox publisher retry güvenliği).
     * NOT: {@code Map.of} null değer kabul etmez — bu eski sürümde null payload
     * (KEY_UPLOADED vb.) sessiz NPE'ye yol açıyordu; HashMap + null→"" ile fix.
     */
    public void appendRecord(String electionId, String recordType, String trackingCode,
                             String ballotHash, String payload, String idempotencyKey) {
        try {
            Map<String, Object> body = new HashMap<>();
            body.put("electionId", electionId);
            body.put("recordType", recordType);
            body.put("trackingCode", trackingCode == null ? "" : trackingCode);
            body.put("ballotHash", ballotHash == null ? "" : ballotHash);
            body.put("payload", payload == null ? "" : payload);
            if (idempotencyKey != null && !idempotencyKey.isBlank()) {
                body.put("idempotencyKey", idempotencyKey);
            }
            ResponseEntity<String> response = restTemplate.postForEntity(
                    bulletinBoardUrl + "/api/v1/bulletin/internal/records",
                    body,
                    String.class
            );
            log.info("Bulletin record appended: election={}, type={}, status={}",
                    electionId, recordType, response.getStatusCode());
        } catch (Exception e) {
            log.error("Bulletin append failed: election={}, type={}, error={}",
                    electionId, recordType, e.getMessage());
            throw e;
        }
    }

    /**
     * Faz 1.3 — Audit record için bulletin hash-zincirini (tüm kayıtlar, id
     * sırasıyla) okur. Denetçiye sunulan tam election record'un parçası.
     */
    public List<Map<String, Object>> fetchElectionRecord(String electionId) {
        ResponseEntity<List<Map<String, Object>>> resp = restTemplate.exchange(
                bulletinBoardUrl + "/api/v1/bulletin/elections/" + electionId + "/record",
                HttpMethod.GET,
                null,
                new ParameterizedTypeReference<List<Map<String, Object>>>() {});
        return resp.getBody() == null ? List.of() : resp.getBody();
    }
}
