package com.cepsandik.communityservice.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

@Configuration
@EnableJpaAuditing
public class JpaConfig {
    // JPA Auditing konfigürasyonu
    // CreatedDate ve LastModifiedDate anotasyonlarının çalışması için gerekli
}
