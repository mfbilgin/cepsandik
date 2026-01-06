package com.cepsandik.userservice.service;

import com.cepsandik.userservice.annotations.LogAudit;
import com.cepsandik.userservice.common.MessageConstants;
import com.cepsandik.userservice.dtos.responses.TwoFactorSetupResponse;
import com.cepsandik.userservice.exceptions.ApiException;
import com.cepsandik.userservice.models.TwoFactorAuth;
import com.cepsandik.userservice.models.User;
import com.cepsandik.userservice.repositories.TwoFactorAuthRepository;
import com.cepsandik.userservice.repositories.UserRepository;
import dev.samstevens.totp.code.*;
import dev.samstevens.totp.exceptions.QrGenerationException;
import dev.samstevens.totp.qr.QrData;
import dev.samstevens.totp.qr.QrGenerator;
import dev.samstevens.totp.qr.ZxingPngQrGenerator;
import dev.samstevens.totp.secret.DefaultSecretGenerator;
import dev.samstevens.totp.secret.SecretGenerator;
import dev.samstevens.totp.time.SystemTimeProvider;
import dev.samstevens.totp.time.TimeProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.UUID;
import java.util.stream.IntStream;

/**
 * Two-Factor Authentication Service using TOTP
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TwoFactorAuthService {

    private final TwoFactorAuthRepository twoFactorAuthRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    private static final String ISSUER = "CepSandik";
    private static final int BACKUP_CODE_COUNT = 8;
    private static final int BACKUP_CODE_LENGTH = 8;

    /**
     * Initiates 2FA setup - generates secret and QR code
     */
    @Transactional
    @LogAudit(action = "2FA SETUP INIT")
    public TwoFactorSetupResponse setupTwoFactor(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, MessageConstants.USER_NOT_FOUND));

        // Check if already enabled
        var existing = twoFactorAuthRepository.findByUserId(user.getId());
        if (existing.isPresent() && existing.get().isEnabled()) {
            throw new ApiException(HttpStatus.CONFLICT, "2FA zaten etkin");
        }

        // Generate secret
        SecretGenerator secretGenerator = new DefaultSecretGenerator();
        String secret = secretGenerator.generate();

        // Generate backup codes
        String[] backupCodes = generateBackupCodes();
        String hashedBackupCodes = hashBackupCodes(backupCodes);

        // Create or update 2FA record
        TwoFactorAuth twoFactorAuth;
        if (existing.isPresent()) {
            twoFactorAuth = existing.get();
            twoFactorAuth.setSecretKey(secret);
            twoFactorAuth.setBackupCodes(hashedBackupCodes);
            twoFactorAuth.setEnabled(false);
        } else {
            twoFactorAuth = TwoFactorAuth.builder()
                    .user(user)
                    .secretKey(secret)
                    .backupCodes(hashedBackupCodes)
                    .enabled(false)
                    .build();
        }
        twoFactorAuthRepository.save(twoFactorAuth);

        // Generate QR code URI
        String qrCodeUri = generateQrCodeUri(secret, user.getEmail());

        log.info("2FA setup initiated: userId={}", user.getId());

        return new TwoFactorSetupResponse(qrCodeUri, secret, backupCodes);
    }

    /**
     * Verifies and enables 2FA
     */
    @Transactional
    @LogAudit(action = "2FA ENABLE")
    public void enableTwoFactor(String email, String code) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, MessageConstants.USER_NOT_FOUND));

        TwoFactorAuth twoFactorAuth = twoFactorAuthRepository.findByUserId(user.getId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "2FA kurulumu bulunamadı"));

        if (twoFactorAuth.isEnabled()) {
            throw new ApiException(HttpStatus.CONFLICT, "2FA zaten etkin");
        }

        // Verify code
        if (!verifyCode(twoFactorAuth.getSecretKey(), code)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Geçersiz doğrulama kodu");
        }

        twoFactorAuth.setEnabled(true);
        twoFactorAuth.setVerifiedAt(LocalDateTime.now());
        twoFactorAuthRepository.save(twoFactorAuth);

        log.info("2FA enabled: userId={}", user.getId());
    }

    /**
     * Disables 2FA
     */
    @Transactional
    @LogAudit(action = "2FA DISABLE")
    public void disableTwoFactor(String email, String password) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, MessageConstants.USER_NOT_FOUND));

        // Verify password
        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, MessageConstants.INCORRECT_PASSWORD);
        }

        twoFactorAuthRepository.deleteByUserId(user.getId());

        log.info("2FA disabled: userId={}", user.getId());
    }

    /**
     * Verifies TOTP code during login
     */
    public boolean verifyTwoFactorCode(UUID userId, String code) {
        TwoFactorAuth twoFactorAuth = twoFactorAuthRepository.findByUserId(userId)
                .orElse(null);

        if (twoFactorAuth == null || !twoFactorAuth.isEnabled()) {
            return true; // 2FA not enabled, skip verification
        }

        // Try TOTP code first
        if (verifyCode(twoFactorAuth.getSecretKey(), code)) {
            return true;
        }

        // Try backup code
        return verifyBackupCode(twoFactorAuth, code);
    }

    /**
     * Checks if user has 2FA enabled
     */
    public boolean isTwoFactorEnabled(UUID userId) {
        return twoFactorAuthRepository.findByUserId(userId)
                .map(TwoFactorAuth::isEnabled)
                .orElse(false);
    }

    /**
     * Gets 2FA status for a user
     */
    public boolean getTwoFactorStatus(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, MessageConstants.USER_NOT_FOUND));

        return isTwoFactorEnabled(user.getId());
    }

    // ========== Helper Methods ==========

    private boolean verifyCode(String secret, String code) {
        TimeProvider timeProvider = new SystemTimeProvider();
        CodeGenerator codeGenerator = new DefaultCodeGenerator();
        CodeVerifier verifier = new DefaultCodeVerifier(codeGenerator, timeProvider);
        return verifier.isValidCode(secret, code);
    }

    private String generateQrCodeUri(String secret, String email) {
        QrData data = new QrData.Builder()
                .label(email)
                .secret(secret)
                .issuer(ISSUER)
                .algorithm(HashingAlgorithm.SHA1)
                .digits(6)
                .period(30)
                .build();

        try {
            QrGenerator generator = new ZxingPngQrGenerator();
            byte[] imageData = generator.generate(data);
            return "data:image/png;base64," + Base64.getEncoder().encodeToString(imageData);
        } catch (QrGenerationException e) {
            log.error("QR code generation failed", e);
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "QR kodu oluşturulamadı");
        }
    }

    private String[] generateBackupCodes() {
        SecureRandom random = new SecureRandom();
        return IntStream.range(0, BACKUP_CODE_COUNT)
                .mapToObj(i -> {
                    StringBuilder code = new StringBuilder();
                    for (int j = 0; j < BACKUP_CODE_LENGTH; j++) {
                        code.append(random.nextInt(10));
                    }
                    return code.toString();
                })
                .toArray(String[]::new);
    }

    private String hashBackupCodes(String[] codes) {
        return String.join(",",
                java.util.Arrays.stream(codes)
                        .map(passwordEncoder::encode)
                        .toArray(String[]::new));
    }

    private boolean verifyBackupCode(TwoFactorAuth twoFactorAuth, String code) {
        if (twoFactorAuth.getBackupCodes() == null || twoFactorAuth.getBackupCodes().isEmpty()) {
            return false;
        }

        String[] hashedCodes = twoFactorAuth.getBackupCodes().split(",");
        for (int i = 0; i < hashedCodes.length; i++) {
            if (passwordEncoder.matches(code, hashedCodes[i])) {
                // Invalidate used backup code
                hashedCodes[i] = "USED";
                twoFactorAuth.setBackupCodes(String.join(",", hashedCodes));
                twoFactorAuthRepository.save(twoFactorAuth);
                log.info("Backup code used: userId={}", twoFactorAuth.getUser().getId());
                return true;
            }
        }
        return false;
    }
}
