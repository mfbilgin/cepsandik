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

/**
 * Community Service ile dahili iletişim istemcisi.
 * Docker ağı üzerinden community-service'in internal endpoint'lerini çağırır.
 */
@Component
@Slf4j
public class CommunityServiceClient {

    private final RestTemplate restTemplate;
    private final String communityServiceUrl;

    public CommunityServiceClient(
            RestTemplate restTemplate,
            @Value("${app.community-service.url:http://community-service:8083}") String communityServiceUrl) {
        this.restTemplate = restTemplate;
        this.communityServiceUrl = communityServiceUrl;
    }

    /**
     * Topluluğun onaylı üyelerinin userId listesini döndürür.
     * Hata durumunda boş liste döner (fail-safe).
     */
    public List<String> getMemberUserIds(Long communityId) {
        try {
            String url = communityServiceUrl + "/internal/api/v1/communities/" + communityId + "/member-user-ids";

            ResponseEntity<List<String>> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    null,
                    new ParameterizedTypeReference<List<String>>() {
                    });

            List<String> userIds = response.getBody();
            log.debug("Community {} üye listesi alındı: {} üye", communityId,
                    userIds != null ? userIds.size() : 0);
            return userIds != null ? userIds : Collections.emptyList();

        } catch (RestClientException e) {
            log.error("Community service'den üye listesi alınamadı: communityId={}, hata={}",
                    communityId, e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * Topluluk ismini döndürür.
     */
    public String getCommunityName(Long communityId) {
        if (communityId == null) return null;
        try {
            String url = communityServiceUrl + "/internal/api/v1/communities/" + communityId + "/name";
            return restTemplate.getForObject(url, String.class);
        } catch (RestClientException e) {
            log.error("Community name alınamadı: communityId={}, hata={}", communityId, e.getMessage());
            return "Topluluk #" + communityId;
        }
    }
}
