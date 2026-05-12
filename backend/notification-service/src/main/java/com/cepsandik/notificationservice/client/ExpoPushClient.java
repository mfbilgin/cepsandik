package com.cepsandik.notificationservice.client;

import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

@Component
@Slf4j
public class ExpoPushClient {

    private final RestTemplate restTemplate;
    private final String expoApiUrl;

    public ExpoPushClient(
            @Value("${app.expo.api-url:https://exp.host/--/api/v2/push/send}") String expoApiUrl) {
        this.restTemplate = new RestTemplate();
        this.expoApiUrl = expoApiUrl;
    }

    public void sendPushNotification(String expoToken, String title, String body, Map<String, Object> data) {
        if (expoToken == null || !expoToken.startsWith("ExponentPushToken")) {
            log.warn("Invalid Expo push token: {}", expoToken);
            return;
        }

        try {
            PushRequest request = PushRequest.builder()
                    .to(expoToken)
                    .title(title)
                    .body(body)
                    .data(data)
                    .sound("default")
                    .build();

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<PushRequest> entity = new HttpEntity<>(request, headers);

            restTemplate.postForEntity(expoApiUrl, entity, String.class);
            log.debug("Push notification sent to {}", expoToken);
        } catch (Exception e) {
            log.error("Failed to send push notification to {}: {}", expoToken, e.getMessage());
        }
    }

    @Data
    @Builder
    public static class PushRequest {
        private String to;
        private String title;
        private String body;
        private Map<String, Object> data;
        private String sound;
        private Integer badge;
    }
}
