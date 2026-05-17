package com.cepsandik.electionservice.config;

import com.cepsandik.electionservice.security.InternalJwtService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.web.client.RestTemplate;

import java.util.List;

@Configuration
public class RestTemplateConfig {

    /**
     * RestTemplate, tüm giden isteklere X-Internal-Auth (service-to-service
     * internal JWT) ekler. community-service / user-service'in InternalJwtFilter'ı
     * bunu zorunlu kılıyor; aksi halde 401 "Internal token eksik" döner ve
     * selectGuardians 0 üye görür.
     */
    @Bean
    public RestTemplate restTemplate(InternalJwtService internalJwtService) {
        RestTemplate restTemplate = new RestTemplate();
        ClientHttpRequestInterceptor internalAuth = (request, body, execution) -> {
            if (!request.getHeaders().containsKey("X-Internal-Auth")) {
                request.getHeaders().add("X-Internal-Auth", internalJwtService.generateServiceToken());
            }
            return execution.execute(request, body);
        };
        restTemplate.setInterceptors(List.of(internalAuth));
        return restTemplate;
    }
}
