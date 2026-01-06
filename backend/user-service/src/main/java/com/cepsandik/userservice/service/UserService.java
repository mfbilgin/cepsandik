package com.cepsandik.userservice.service;

import com.cepsandik.userservice.annotations.LogAudit;
import com.cepsandik.userservice.common.MessageConstants;
import com.cepsandik.userservice.dtos.requests.EmailChangeRequest;
import com.cepsandik.userservice.dtos.requests.PasswordChangeRequest;
import com.cepsandik.userservice.dtos.requests.UpdateProfileRequest;
import com.cepsandik.userservice.dtos.responses.UserResponse;
import com.cepsandik.userservice.exceptions.ApiException;
import com.cepsandik.userservice.mappers.UserMapper;
import com.cepsandik.userservice.models.EmailChangeToken;
import com.cepsandik.userservice.repositories.EmailChangeTokenRepository;
import com.cepsandik.userservice.repositories.RefreshTokenRepository;
import com.cepsandik.userservice.repositories.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserService {
    private final UserRepository userRepo;
    private final PasswordEncoder encoder;
    private final RefreshTokenRepository refreshTokenRepository;
    private final EmailChangeTokenRepository emailChangeTokenRepository;
    private final EmailService emailService;
    private final FileUploadService fileUploadService;

    @LogAudit(action = "PROFILE VIEWING")
    public UserResponse me(String email) {
        var user = userRepo.findByEmail(email)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, MessageConstants.USER_NOT_FOUND));
        return UserMapper.toResponse(user);
    }

    @LogAudit(action = "PROFILE UPDATE")
    public UserResponse updateMe(String email, UpdateProfileRequest req) {
        var user = userRepo.findByEmail(email)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, MessageConstants.USER_NOT_FOUND));

        if (req.firstName() != null && !req.firstName().isBlank())
            user.setFirstName(req.firstName());
        if (req.lastName() != null && !req.lastName().isBlank())
            user.setLastName(req.lastName());
        userRepo.save(user);
        return UserMapper.toResponse(user);
    }

    @LogAudit(action = "PASSWORD CHANGE")
    public void changePassword(String email, PasswordChangeRequest req) {
        var user = userRepo.findByEmail(email)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, MessageConstants.USER_NOT_FOUND));
        if (Objects.equals(req.oldPassword(), req.newPassword()))
            throw new ApiException(HttpStatus.CONFLICT, MessageConstants.PASSWORDS_SAME);
        if (!encoder.matches(req.oldPassword(), user.getPasswordHash()))
            throw new ApiException(HttpStatus.UNAUTHORIZED, MessageConstants.INCORRECT_PASSWORD);

        user.setPasswordHash(encoder.encode(req.newPassword()));
        userRepo.save(user);
    }

    @LogAudit(action = "DELETE ACCOUNT")
    public void deleteMe(String email) {
        var user = userRepo.findByEmail(email)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, MessageConstants.USER_NOT_FOUND));
        var userTokens = refreshTokenRepository.findByUserId(user.getId());
        if (!userTokens.isEmpty()) {
            refreshTokenRepository.deleteAll(userTokens);
        }
        userRepo.delete(user);
    }

    /**
     * Kullanıcının tüm cihazlardan çıkış yapmasını sağlar.
     */
    @LogAudit(action = "LOGOUT ALL DEVICES")
    public void logoutAllDevices(String email) {
        var user = userRepo.findByEmail(email)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, MessageConstants.USER_NOT_FOUND));

        var tokens = refreshTokenRepository.findByUserId(user.getId());
        if (tokens != null && !tokens.isEmpty()) {
            refreshTokenRepository.deleteAll(tokens);
        }
    }

    /**
     * Email değişikliği talebini başlatır.
     * Eski email'e bildirim, yeni email'e doğrulama linki gönderir.
     */
    @Transactional
    @LogAudit(action = "EMAIL CHANGE REQUEST")
    public void requestEmailChange(String currentEmail, EmailChangeRequest req) {
        var user = userRepo.findByEmail(currentEmail)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, MessageConstants.USER_NOT_FOUND));

        // Parola doğrulama
        if (!encoder.matches(req.currentPassword(), user.getPasswordHash())) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, MessageConstants.INCORRECT_PASSWORD);
        }

        // Yeni email zaten kullanımda mı?
        if (userRepo.findByEmail(req.newEmail()).isPresent()) {
            throw new ApiException(HttpStatus.CONFLICT, MessageConstants.EMAIL_EXISTS);
        }

        // Aynı email mi?
        if (currentEmail.equalsIgnoreCase(req.newEmail())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Yeni email mevcut email ile aynı olamaz");
        }

        // Token oluştur
        var token = EmailChangeToken.builder()
                .user(user)
                .oldEmail(currentEmail)
                .newEmail(req.newEmail())
                .token(UUID.randomUUID().toString())
                .expiresAt(LocalDateTime.now().plusHours(2))
                .used(false)
                .build();

        emailChangeTokenRepository.save(token);

        // Emailler gönder
        emailService.sendEmailChangeNotification(currentEmail, user.getFirstName(), req.newEmail());
        emailService.sendEmailChangeVerification(req.newEmail(), user.getFirstName(), token.getToken());
    }

    /**
     * Email değişikliğini onaylar.
     */
    @Transactional
    @LogAudit(action = "EMAIL CHANGE CONFIRM")
    public void confirmEmailChange(String token) {
        var emailChangeToken = emailChangeTokenRepository.findByToken(token)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, MessageConstants.INVALID_TOKEN));

        // Token kullanılmış mı?
        if (emailChangeToken.isUsed()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Bu bağlantı daha önce kullanılmış");
        }

        // Token süresi dolmuş mu?
        if (emailChangeToken.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, MessageConstants.INVALID_TOKEN);
        }

        // Yeni email hala müsait mi?
        if (userRepo.findByEmail(emailChangeToken.getNewEmail()).isPresent()) {
            throw new ApiException(HttpStatus.CONFLICT, MessageConstants.EMAIL_EXISTS);
        }

        // Email'i güncelle
        var user = emailChangeToken.getUser();
        user.setEmail(emailChangeToken.getNewEmail());
        userRepo.save(user);

        // Token'ı kullanılmış olarak işaretle
        emailChangeToken.setUsed(true);
        emailChangeTokenRepository.save(emailChangeToken);
    }

    // ========== Profile Image ==========

    /**
     * Profil resmini günceller
     */
    @LogAudit(action = "PROFILE IMAGE UPDATE")
    public UserResponse updateProfileImage(String email, String imageUrl) {
        var user = userRepo.findByEmail(email)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, MessageConstants.USER_NOT_FOUND));

        user.setProfileImage(imageUrl);
        userRepo.save(user);
        return UserMapper.toResponse(user);
    }

    /**
     * Profil resmini siler
     */
    @LogAudit(action = "PROFILE IMAGE DELETE")
    public UserResponse deleteProfileImage(String email) {
        var user = userRepo.findByEmail(email)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, MessageConstants.USER_NOT_FOUND));

        // S3'ten sil
        if (user.getProfileImage() != null) {
            fileUploadService.deleteProfileImage(user.getProfileImage());
        }

        user.setProfileImage(null);
        userRepo.save(user);
        return UserMapper.toResponse(user);
    }
}
