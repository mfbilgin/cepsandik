package com.cepsandik.notificationservice.client;

import com.cepsandik.notificationservice.dto.UserNotificationDetailsResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

@Component
@Slf4j
public class UserServiceClient {

    private final RestTemplate restTemplate;
    private final String userServiceUrl;

    public UserServiceClient(
            @Value("${app.user-service.url:http://user-service:8080}") String userServiceUrl) {
        this.restTemplate = new RestTemplate();
        this.userServiceUrl = userServiceUrl;
    }

    public List<UserNotificationDetailsResponse> getUserDetails(List<UUID> userIds) {
        if (userIds == null || userIds.isEmpty()) return Collections.emptyList();
        
        try {
            String url = userServiceUrl + "/internal/api/v1/users/notification-details";
            HttpEntity<List<UUID>> request = new HttpEntity<>(userIds);
            
            return restTemplate.exchange(
                    url,
                    HttpMethod.POST,
                    request,
                    new ParameterizedTypeReference<List<UserNotificationDetailsResponse>>() {}
            ).getBody();
        } catch (Exception e) {
            log.error("User details fetch failed: ", e);
            return Collections.emptyList();
        }
    }
}
