package com.cepsandik.electionservice.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

import java.security.SecureRandom;

@Configuration
public class AccessCodeConfig {

    @Value("${app.access-code.length:6}")
    private int codeLength;

    @Value("${app.access-code.characters:ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789}")
    private String characters;

    private final SecureRandom random = new SecureRandom();

    /**
     * Benzersiz 6 haneli alfanumerik erişim kodu üretir
     * Örnek: "A3X7K9"
     */
    public String generateCode() {
        StringBuilder code = new StringBuilder(codeLength);
        for (int i = 0; i < codeLength; i++) {
            code.append(characters.charAt(random.nextInt(characters.length())));
        }
        return code.toString();
    }
}
