package com.cepsandik.userservice.controllers;

import com.cepsandik.userservice.common.MessageConstants;
import com.cepsandik.userservice.dtos.requests.*;
import com.cepsandik.userservice.dtos.responses.ApiResponse;
import com.cepsandik.userservice.dtos.responses.AuthResponse;
import com.cepsandik.userservice.dtos.responses.UserResponse;
import com.cepsandik.userservice.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication", description = "Kayıt, giriş, doğrulama, parola sıfırlama işlemleri")
public class AuthController {
    private final AuthService auth;

    @Operation(summary = "Yeni kullanıcı kaydı oluşturur")
    @PostMapping("/register")
    public ResponseEntity<ApiResponse<UserResponse>> register(@Valid @RequestBody RegisterRequest req) {
        UserResponse res = auth.register(req);
        return ResponseEntity.ok(ApiResponse.ok(MessageConstants.REGISTER_SUCCESS, res));
    }

    @Operation(summary = "E-posta doğrulama linkini tekrar gönderir")
    @PostMapping("/resend-verification")
    public ResponseEntity<ApiResponse<Void>> resendVerification(
            @Valid @RequestBody com.cepsandik.userservice.dtos.requests.ResendVerificationRequest req) {
        auth.resendVerification(req.email(), req.password());
        return ResponseEntity.ok(ApiResponse.ok("Doğrulama e-postası tekrar gönderildi", null));
    }

    @Operation(summary = "Kullanıcı girişi yapar")
    @PostMapping("/login")
    public ResponseEntity<ApiResponse<AuthResponse>> login(@Valid @RequestBody LoginRequest req) {
        AuthResponse res = auth.login(req);
        return ResponseEntity.ok(ApiResponse.ok(MessageConstants.LOGIN_SUCCESS, res));
    }

    @Operation(summary = "JWT token yeniler")
    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<AuthResponse>> refresh(@Valid @RequestBody TokenRefreshRequest req) {
        AuthResponse res = auth.refresh(req);
        return ResponseEntity.ok(ApiResponse.ok(MessageConstants.REFRESH_SUCCESS, res));
    }

    @Operation(summary = "Kullanıcının oturumunu kapatır")
    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout(
            @Valid @RequestBody TokenRefreshRequest req,
            @RequestHeader("Authorization") String authHeader) {
        String accessToken = authHeader.substring(7); // "Bearer " prefix'ini kaldır
        auth.logout(req.refreshToken(), accessToken);
        return ResponseEntity.ok(ApiResponse.ok(MessageConstants.LOGOUT_SUCCESS));
    }

    @Operation(summary = "Parola sıfırlama bağlantısı gönderir")
    @PostMapping("/forgot-password")
    public ResponseEntity<ApiResponse<Void>> resetRequest(@Valid @RequestBody PasswordResetRequest req) {
        auth.requestPasswordReset(req);
        return ResponseEntity.ok(ApiResponse.ok(MessageConstants.PASSWORD_RESET_SENT));
    }

    @Operation(summary = "Yeni parola belirler")
    @PostMapping("/reset-password")
    public ResponseEntity<ApiResponse<Void>> resetPerform(@Valid @RequestBody PasswordResetPerformRequest req) {
        auth.performPasswordReset(req);
        return ResponseEntity.ok(ApiResponse.ok(MessageConstants.PASSWORD_RESET_SUCCESS));
    }

    @Operation(summary = "Kullanıcı hesabını aktifleştirir")
    @PutMapping("/activate")
    public ResponseEntity<ApiResponse<Void>> activateUser(@Valid @RequestBody ActivateRequest req) {
        auth.activateUser(req);
        return ResponseEntity.ok(ApiResponse.ok(MessageConstants.ACCOUNT_ACTIVATED));
    }

    @Operation(summary = "2FA ile giriş tamamlar")
    @PostMapping("/login/2fa")
    public ResponseEntity<ApiResponse<AuthResponse>> loginWith2FA(@RequestBody java.util.Map<String, String> req) {
        String tempToken = req.get("tempToken");
        String code = req.get("code");

        if (tempToken == null || tempToken.isEmpty() || code == null || code.isEmpty()) {
            throw new com.cepsandik.userservice.exceptions.ApiException(
                    org.springframework.http.HttpStatus.BAD_REQUEST, "tempToken ve code zorunludur");
        }

        AuthResponse res = auth.loginWith2FA(tempToken, code);
        return ResponseEntity.ok(ApiResponse.ok(MessageConstants.LOGIN_SUCCESS, res));
    }
}
