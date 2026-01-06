package com.cepsandik.userservice.controllers;

import com.cepsandik.userservice.dtos.requests.TwoFactorVerifyRequest;
import com.cepsandik.userservice.dtos.responses.ApiResponse;
import com.cepsandik.userservice.dtos.responses.TwoFactorSetupResponse;
import com.cepsandik.userservice.models.User;
import com.cepsandik.userservice.service.TwoFactorAuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/users/me/2fa")
@RequiredArgsConstructor
@Tag(name = "Two-Factor Authentication", description = "İki faktörlü doğrulama işlemleri")
public class TwoFactorAuthController {

    private final TwoFactorAuthService twoFactorAuthService;

    @Operation(summary = "2FA durumunu sorgular")
    @GetMapping("/status")
    public ResponseEntity<ApiResponse<Map<String, Boolean>>> getStatus(Authentication authentication) {
        User user = (User) authentication.getPrincipal();
        boolean enabled = twoFactorAuthService.getTwoFactorStatus(user.getEmail());
        return ResponseEntity.ok(ApiResponse.ok("2FA durumu", Map.of("enabled", enabled)));
    }

    @Operation(summary = "2FA kurulumunu başlatır")
    @PostMapping("/setup")
    public ResponseEntity<ApiResponse<TwoFactorSetupResponse>> setup(Authentication authentication) {
        User user = (User) authentication.getPrincipal();
        TwoFactorSetupResponse response = twoFactorAuthService.setupTwoFactor(user.getEmail());
        return ResponseEntity
                .ok(ApiResponse.ok("2FA kurulumu başlatıldı. QR kodu tarayın ve doğrulama kodunu girin.", response));
    }

    @Operation(summary = "2FA'yı etkinleştirir")
    @PostMapping("/enable")
    public ResponseEntity<ApiResponse<Void>> enable(
            Authentication authentication,
            @Valid @RequestBody TwoFactorVerifyRequest request) {
        User user = (User) authentication.getPrincipal();
        twoFactorAuthService.enableTwoFactor(user.getEmail(), request.code());
        return ResponseEntity.ok(ApiResponse.ok("2FA başarıyla etkinleştirildi"));
    }

    @Operation(summary = "2FA'yı devre dışı bırakır")
    @PostMapping("/disable")
    public ResponseEntity<ApiResponse<Void>> disable(
            Authentication authentication,
            @RequestBody Map<String, String> request) {
        User user = (User) authentication.getPrincipal();
        String password = request.get("password");
        if (password == null || password.isEmpty()) {
            return ResponseEntity.badRequest().body(ApiResponse.fail("Parola gereklidir"));
        }
        twoFactorAuthService.disableTwoFactor(user.getEmail(), password);
        return ResponseEntity.ok(ApiResponse.ok("2FA devre dışı bırakıldı"));
    }
}
