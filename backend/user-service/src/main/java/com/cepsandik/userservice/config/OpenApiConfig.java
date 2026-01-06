package com.cepsandik.userservice.config;

import io.swagger.v3.oas.models.*;
import io.swagger.v3.oas.models.info.*;
import io.swagger.v3.oas.models.security.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI userServiceOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Cep Sandık - User Service API")
                        .version("v1.0")
                        .description("""
                                Bu servis, kullanıcı kimlik doğrulama ve hesap yönetimi işlemlerini sağlar.
                                JWT tabanlı kimlik doğrulama kullanır.
                                """)
                        .contact(new Contact()
                                .name("Muhammet Bilgin")
                                .email("mf.bilgin0@gmail.com")
                                .url("https://github.com/mfbilgin"))
                )
                .components(new Components()
                        .addSecuritySchemes("bearerAuth", new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement().addList("bearerAuth", List.of("read", "write")));
    }
}
