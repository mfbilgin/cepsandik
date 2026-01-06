package com.cepsandik.communityservice.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableScheduling
public class SchedulingConfig {
    // Zamanlanmış görevler için konfigürasyon
    // Süresi dolmuş davetlerin temizlenmesi vb. için gerekli
}
