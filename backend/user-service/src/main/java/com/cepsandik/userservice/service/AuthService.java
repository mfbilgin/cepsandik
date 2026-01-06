package com.cepsandik.userservice.service;

import com.cepsandik.userservice.annotations.LogAudit;
import com.cepsandik.userservice.common.MessageConstants;
import com.cepsandik.userservice.dtos.requests.*;
import com.cepsandik.userservice.dtos.responses.AuthResponse;
import com.cepsandik.userservice.dtos.responses.UserResponse;
import com.cepsandik.userservice.exceptions.ApiException;
import com.cepsandik.userservice.mappers.UserMapper;
import com.cepsandik.userservice.models.RefreshToken;
import com.cepsandik.userservice.models.PasswordResetToken;
import com.cepsandik.userservice.models.User;
import com.cepsandik.userservice.repositories.PasswordResetTokenRepository;
import com.cepsandik.userservice.repositories.RefreshTokenRepository;
import com.cepsandik.userservice.repositories.UserRepository;
import com.cepsandik.userservice.security.JwtService;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshRepository;
    private final PasswordResetTokenRepository resetRepository;
    private final PasswordEncoder encoder;
    private final JwtService jwt;
    private final EmailProducer emailProducer;
    private final LoginAttemptService loginAttemptService;
    private final TwoFactorAuthService twoFactorAuthService;
    private final TokenBlacklistService tokenBlacklistService;

    @LogAudit(action = "REGISTER")
    public UserResponse register(RegisterRequest req) {
        var existingUser = userRepository.findAnyByEmail(req.email());
        if (existingUser.isPresent()) {
            var user = existingUser.get();
            if (user.getDeletedAt() != null)
                throw new ApiException(HttpStatus.CONFLICT, MessageConstants.ACCOUNT_IS_SOFT_DELETED);
            throw new ApiException(HttpStatus.CONFLICT, MessageConstants.EMAIL_EXISTS);
        }
        var user = User.builder()
                .firstName(req.firstName())
                .lastName(req.lastName())
                .email(req.email())
                .passwordHash(encoder.encode(req.password()))
                .isActive(true)
                .isVerified(false)
                .verificationToken(UUID.randomUUID().toString())
                .build();

        userRepository.save(user);
        emailProducer.sendVerificationEmail(user.getEmail(), user.getFirstName(), user.getVerificationToken());
        return UserMapper.toResponse(user);
    }

    @LogAudit(action = "EMAIL VERIFICATION")
    public String verifyEmail(String token) {
        var user = userRepository.findByVerificationToken(token)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, MessageConstants.INVALID_TOKEN));

        user.setVerifiedAt(LocalDateTime.now());
        user.setVerified(true);
        user.setVerificationToken(null);
        userRepository.save(user);

        return MessageConstants.ACCOUNT_VERIFIED;
    }

    @LogAudit(action = "RESEND VERIFICATION")
    public void resendVerification(String email, String password) {
        var user = userRepository.findAnyByEmail(email)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, MessageConstants.INVALID_CREDENTIALS));

        // Şifre kontrolü
        if (user.getPasswordHash() == null || !encoder.matches(password, user.getPasswordHash())) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, MessageConstants.INVALID_CREDENTIALS);
        }

        if (user.isVerified()) {
            throw new ApiException(HttpStatus.CONFLICT, "E-posta adresi zaten doğrulanmış");
        }

        // Yeni verification token oluştur
        user.setVerificationToken(UUID.randomUUID().toString());
        userRepository.save(user);

        // Email gönder
        emailProducer.sendVerificationEmail(user.getEmail(), user.getFirstName(), user.getVerificationToken());
    }

    @LogAudit(action = "LOGIN")
    public AuthResponse login(LoginRequest req) {
        // Önce hesap engellenmiş mi kontrol et
        if (loginAttemptService.isBlocked(req.email())) {
            long remainingMinutes = loginAttemptService.getRemainingLockoutMinutes(req.email());
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS,
                    String.format(MessageConstants.ACCOUNT_LOCKED, remainingMinutes));
        }

        var user = userRepository.findAnyByEmail(req.email())
                .orElseThrow(() -> {
                    loginAttemptService.recordFailedAttempt(req.email());
                    return new ApiException(HttpStatus.UNAUTHORIZED, MessageConstants.INVALID_CREDENTIALS);
                });

        // Password hash null kontrolü
        if (user.getPasswordHash() == null || user.getPasswordHash().isEmpty()) {
            loginAttemptService.recordFailedAttempt(req.email());
            throw new ApiException(HttpStatus.UNAUTHORIZED, MessageConstants.INVALID_CREDENTIALS);
        }

        if (!encoder.matches(req.password(), user.getPasswordHash())) {
            loginAttemptService.recordFailedAttempt(req.email());
            throw new ApiException(HttpStatus.UNAUTHORIZED, MessageConstants.INVALID_CREDENTIALS);
        }

        if (!user.isActive())
            throw new ApiException(HttpStatus.FORBIDDEN, MessageConstants.ACCOUNT_PASSIVE);
        if (!user.isVerified())
            throw new ApiException(HttpStatus.FORBIDDEN, MessageConstants.EMAIL_NOT_VERIFIED);

        // 2FA kontrolü
        if (twoFactorAuthService.isTwoFactorEnabled(user.getId())) {
            // 2FA etkin - geçici token oluştur ve 2FA kodu iste
            String tempToken = jwt.generateTempToken(user);
            return AuthResponse.requires2FA(tempToken);
        }

        // Başarılı giriş - denemeleri sıfırla
        loginAttemptService.clearAttempts(req.email());

        Map<String, Object> accessTokenData = jwt.generateAccessTokenWithClaims(user);
        var access = (String) accessTokenData.get("token");
        var expireDate = (long) accessTokenData.get("expiration");
        var refresh = createRefreshToken(user);
        return AuthResponse.bearer(access, refresh.getToken(), expireDate);
    }

    /**
     * 2FA ile login tamamlama
     */
    @LogAudit(action = "LOGIN_2FA")
    public AuthResponse loginWith2FA(String tempToken, String twoFactorCode) {
        // Geçici token'ı doğrula ve kullanıcı id'sini al
        UUID userId = jwt.validateTempTokenAndGetUserId(tempToken);
        if (userId == null) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, MessageConstants.INVALID_2FA_TOKEN);
        }

        var user = userRepository.findById(userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, MessageConstants.USER_NOT_FOUND));

        // 2FA kodunu doğrula
        if (!twoFactorAuthService.verifyTwoFactorCode(userId, twoFactorCode)) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Geçersiz 2FA kodu");
        }

        // Başarılı giriş - denemeleri sıfırla
        loginAttemptService.clearAttempts(user.getEmail());

        Map<String, Object> accessTokenData = jwt.generateAccessTokenWithClaims(user);
        var access = (String) accessTokenData.get("token");
        var expireDate = (long) accessTokenData.get("expiration");
        var refresh = createRefreshToken(user);
        return AuthResponse.bearer(access, refresh.getToken(), expireDate);
    }

    @LogAudit(action = "TOKEN REFRESH")
    @Transactional
    public AuthResponse refresh(TokenRefreshRequest req) {
        var rt = refreshRepository.findByToken(req.refreshToken())
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, MessageConstants.INVALID_REFRESH_TOKEN));

        var user = userRepository.findById(rt.getUserId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, MessageConstants.USER_NOT_FOUND));

        Map<String, Object> accessTokenData = jwt.generateAccessTokenWithClaims(user);
        var access = (String) accessTokenData.get("token");
        var expireDate = (long) accessTokenData.get("expiration");

        return AuthResponse.bearer(access, rt.getToken(), expireDate);
    }

    @LogAudit(action = "LOGOUT")
    public void logout(String refreshToken, String accessToken) {
        // Refresh token'ı sil
        refreshRepository.findByToken(refreshToken).ifPresent(refreshRepository::delete);

        // Access token'ı blacklist'e ekle (varsa)
        if (accessToken != null && !accessToken.isEmpty()) {
            try {
                long expiration = jwt.extractExpiration(accessToken);
                long remainingSeconds = tokenBlacklistService.calculateRemainingSeconds(expiration);
                if (remainingSeconds > 0) {
                    tokenBlacklistService.blacklistToken(accessToken, remainingSeconds);
                }
            } catch (Exception ignored) {
                // Token parse edilemezse devam et
            }
        }
    }

    @LogAudit(action = "FORGOT PASSWORD")
    public void requestPasswordReset(PasswordResetRequest req) {
        var user = userRepository.findByEmail(req.email())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, MessageConstants.EMAIL_NOT_EXISTS));

        var token = UUID.randomUUID().toString();
        var prt = PasswordResetToken.builder()
                .user(user)
                .resetToken(token)
                .expiresAt(LocalDateTime.now().plusHours(2))
                .used(false)
                .build();
        resetRepository.save(prt);
        emailProducer.sendPasswordResetEmail(user.getEmail(), user.getFirstName(), token);
    }

    @Transactional
    @LogAudit(action = "RESET PASSWORD")
    public void performPasswordReset(PasswordResetPerformRequest req) {
        var prt = resetRepository.findByResetToken(req.resetToken())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, MessageConstants.INVALID_RESET_TOKEN));

        if (prt.isUsed() || prt.getExpiresAt().isBefore(LocalDateTime.now()))
            throw new ApiException(HttpStatus.BAD_REQUEST, MessageConstants.INVALID_RESET_TOKEN);

        var user = prt.getUser();
        user.setPasswordHash(encoder.encode(req.newPassword()));
        userRepository.save(user);

        prt.setUsed(true);
        resetRepository.save(prt);
    }

    @Transactional
    @LogAudit(action = "USER ACTIVATION")
    public void activateUser(ActivateRequest req) {
        var user = userRepository.findAnyByEmail(req.email())
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, MessageConstants.INVALID_CREDENTIALS));

        if (!encoder.matches(req.password(), user.getPasswordHash()))
            throw new ApiException(HttpStatus.UNAUTHORIZED, MessageConstants.INVALID_CREDENTIALS);

        if (user.isActive())
            throw new ApiException(HttpStatus.BAD_REQUEST, MessageConstants.ACCOUNT_ALREADY_ACTIVE);

        user.setActive(true);

        userRepository.save(user);
    }

    private RefreshToken createRefreshToken(User user) {
        var rt = RefreshToken.builder()
                .userId(user.getId())
                .token(UUID.randomUUID().toString())
                .build();

        return refreshRepository.save(rt);
    }
}
