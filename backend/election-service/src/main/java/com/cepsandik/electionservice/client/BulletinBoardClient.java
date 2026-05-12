package com.cepsandik.electionservice.client;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class BulletinBoardClient {

    private final RestTemplate restTemplate;

    @Value("${app.bulletin-board.url:http://localhost:8085}")
    private String bulletinBoardUrl;

    public void appendRecord(String electionId, String recordType, String trackingCode, String ballotHash, String payload) {
        try {
            Map<String, Object> body = Map.of(
                    "electionId", electionId,
                    "recordType", recordType,
                    "trackingCode", trackingCode == null ? "" : trackingCode,
                    "ballotHash", ballotHash == null ? "" : ballotHash,
                    "payload", payload
            );
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
}
