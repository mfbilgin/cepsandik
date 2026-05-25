package com.cepsandik.electionservice.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * User Service ile dahili iletişim istemcisi.
 */
@Component
@Slf4j
public class UserServiceClient {

    private final RestTemplate restTemplate;
    private final String userServiceUrl;

    public UserServiceClient(
            RestTemplate restTemplate,
            @Value("${app.user-service.url:http://user-service:8080}") String userServiceUrl) {
        this.restTemplate = restTemplate;
        this.userServiceUrl = userServiceUrl;
    }

    public List<Map<String, Object>> listAllUsers() {
        try {
            String url = userServiceUrl + "/internal/api/v1/users/all";
            ResponseEntity<List<Map<String, Object>>> response = restTemplate.exchange(
                    url, HttpMethod.GET, null,
                    new ParameterizedTypeReference<List<Map<String, Object>>>() {});
            return response.getBody() != null ? response.getBody() : Collections.emptyList();
        } catch (RestClientException e) {
            log.error("User service'den kullanıcı listesi alınamadı: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * Verilen kullanıcı ID'leri arasından emanetçi olmaya uygun (isGuardianEligible=true) olanları döndürür.
     */
    public List<String> filterEligibleGuardians(List<String> userIds) {
        if (userIds == null || userIds.isEmpty()) return Collections.emptyList();
        
        try {
            String url = userServiceUrl + "/internal/api/v1/users/eligible-guardians";
            
            // POST request to filter IDs
            ResponseEntity<List<String>> response = restTemplate.postForEntity(
                    url,
                    userIds,
                    (Class<List<String>>) (Class<?>) List.class
            );

            List<String> eligibleIds = response.getBody();
            return eligibleIds != null ? eligibleIds : Collections.emptyList();

        } catch (RestClientException e) {
            log.error("User service'den uygun emanetçi listesi alınamadı: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * Tüm sistemden rastgele N adet uygun emanetçi seçer (bağımsız seçimler için).
     */
    public List<String> getRandomEligibleGuardians(int limit) {
        try {
            String url = userServiceUrl + "/internal/api/v1/users/random-guardians?limit=" + limit;

            ResponseEntity<List<String>> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    null,
                    new ParameterizedTypeReference<List<String>>() {});

            return response.getBody() != null ? response.getBody() : Collections.emptyList();

        } catch (RestClientException e) {
            log.error("User service'den rastgele emanetçi alınamadı: {}", e.getMessage());
            return Collections.emptyList();
        }
    }
}
