package com.cepsandik.electionservice.controller;

import com.cepsandik.electionservice.client.CryptoEngineClient;
import com.cepsandik.electionservice.dto.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Mobil istemcinin oy yükünü RSA-OAEP (+AES-GCM) ile şifrelemesi için crypto-engine transit public key.
 */
@RestController
@RequestMapping("/api/v1/elections/crypto")
@RequiredArgsConstructor
@Tag(name = "Crypto transit", description = "Transit RSA public key (crypto-engine)")
public class CryptoTransitController {

    private final CryptoEngineClient cryptoEngineClient;

    @Operation(summary = "Crypto-engine RSA public key (PEM) — mobil oy şifrelemesi")
    @GetMapping("/transit-rsa-public-key")
    public ResponseEntity<ApiResponse<Map<String, String>>> getTransitRsaPublicKey() {
        try {
            String pem = cryptoEngineClient.getPublicKey();
            return ResponseEntity.ok(ApiResponse.success(Map.of("rsaPublicKeyPem", pem)));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(ApiResponse.error("Crypto-engine şu an kullanılamıyor: " + e.getMessage()));
        }
    }
}
