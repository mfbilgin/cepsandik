package com.cepsandik.electionservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;
import jakarta.annotation.PostConstruct;
import java.util.TimeZone;

@SpringBootApplication
@EnableScheduling
public class ElectionServiceApplication {

    @PostConstruct
    public void init() {
        // Zamanlama problemleri için uygulama seviyesinde UTC zorunlu kılıyoruz
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
    }

    public static void main(String[] args) {
        SpringApplication.run(ElectionServiceApplication.class, args);
    }

}
