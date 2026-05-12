package com.cepsandik.communityservice.client;

import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserServiceClient {

    private final RestTemplate restTemplate;

    @Value("${APP_USER_SERVICE_URL:http://user-service:8080}")
    private String userServiceUrl;

    @Data
    public static class UserResponse {
        private String id;
        private String firstName;
        private String lastName;
        private String email;
        private boolean verified;
        private String profileImage;
    }

    /**
     * user-service'den userId listesini kullanarak toplu kullanıcı bilgilerini çeker.
     * Hızlı eşleştirme (O(1)) için Map döner.
     */
    public Map<String, UserResponse> getUsersBatch(List<String> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Collections.emptyMap();
        }

        try {
            String url = userServiceUrl + "/internal/api/v1/users/batch";
            
            HttpHeaders headers = new HttpHeaders();
            headers.set("Content-Type", "application/json");
            
            HttpEntity<List<String>> request = new HttpEntity<>(userIds, headers);
            
            ResponseEntity<List<UserResponse>> response = restTemplate.exchange(
                    url,
                    HttpMethod.POST,
                    request,
                    new ParameterizedTypeReference<List<UserResponse>>() {}
            );

            if (response.getBody() != null) {
                return response.getBody().stream()
                        .collect(Collectors.toMap(UserResponse::getId, u -> u, (existing, replacement) -> existing));
            }
        } catch (Exception e) {
            log.error("user-service'den kullanıcı bilgileri çekilemedi: {}", e.getMessage(), e);
        }

        return Collections.emptyMap();
    }
}
