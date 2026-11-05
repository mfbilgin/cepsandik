package com.cepsandik.userservice.controllers;

import com.cepsandik.userservice.dtos.requests.IntegrityVerifyRequest;
import com.cepsandik.userservice.dtos.responses.ApiResponse;
import com.cepsandik.userservice.dtos.responses.IntegrityChallengeResponse;
import com.cepsandik.userservice.dtos.responses.IntegrityVerdictResponse;
import com.cepsandik.userservice.service.integrity.PlayIntegrityService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Uygulama açılışında cihaz bütünlüğü kontrolü. Oturum gerektirmez
 * (giriş ekranından önce çalışır). Gateway'de genel (default) rate limit'e
 * tabidir; auth'un sıkı brute-force limiti, aynı NAT arkasındaki cihazları
 * (kampüs ağı) gereksiz yere engellemesin diye kullanılmaz.
 */
@RestController
@RequestMapping("/api/v1/integrity")
@RequiredArgsConstructor
@Tag(name = "Device Integrity", description = "Google Play Integrity ile cihaz bütünlüğü doğrulaması")
public class IntegrityController {

    private final PlayIntegrityService integrityService;

    @Operation(summary = "Tek kullanımlık bütünlük challenge'ı üretir")
    @PostMapping("/challenge")
    public ResponseEntity<ApiResponse<IntegrityChallengeResponse>> challenge() {
        return ResponseEntity.ok(ApiResponse.ok(integrityService.issueChallenge()));
    }

    @Operation(summary = "Play Integrity token'ını doğrular ve kararı döner")
    @PostMapping("/verify")
    public ResponseEntity<ApiResponse<IntegrityVerdictResponse>> verify(
            @Valid @RequestBody IntegrityVerifyRequest req) {
        return ResponseEntity.ok(ApiResponse.ok(integrityService.verify(req.nonce(), req.token())));
    }
}
