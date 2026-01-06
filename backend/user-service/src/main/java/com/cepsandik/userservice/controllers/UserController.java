package com.cepsandik.userservice.controllers;

import com.cepsandik.userservice.common.MessageConstants;
import com.cepsandik.userservice.dtos.requests.EmailChangeRequest;
import com.cepsandik.userservice.dtos.requests.PasswordChangeRequest;
import com.cepsandik.userservice.dtos.requests.UpdateProfileRequest;
import com.cepsandik.userservice.dtos.responses.ApiResponse;
import com.cepsandik.userservice.dtos.responses.UserResponse;
import com.cepsandik.userservice.models.User;
import com.cepsandik.userservice.service.FileUploadService;
import com.cepsandik.userservice.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
@Tag(name = "User", description = "Profil görüntüleme, güncelleme, silme ve parola değiştirme işlemleri.")
public class UserController {
    private final UserService userService;
    private final FileUploadService fileUploadService;

    @Operation(summary = "Kullanıcı profil bilgilerini getirir")
    @GetMapping("/me")
    public ResponseEntity<ApiResponse<UserResponse>> me(Authentication authentication) {
        User user = (User) authentication.getPrincipal();
        String email = user.getEmail();
        UserResponse res = userService.me(email);
        return ResponseEntity.ok(ApiResponse.ok(MessageConstants.PROFILE_FETCHED, res));
    }

    @Operation(summary = "Kullanıcı profil bilgilerini günceller")
    @PutMapping("/me")
    public ResponseEntity<ApiResponse<UserResponse>> updateMe(Authentication authentication,
            @Valid @RequestBody UpdateProfileRequest req) {
        User user = (User) authentication.getPrincipal();
        String email = user.getEmail();
        UserResponse res = userService.updateMe(email, req);
        return ResponseEntity.ok(ApiResponse.ok(MessageConstants.PROFILE_UPDATED, res));
    }

    @Operation(summary = "Kullanıcı parolasını değiştirir")
    @PutMapping("/me/password")
    public ResponseEntity<ApiResponse<Void>> changePassword(Authentication authentication,
            @Valid @RequestBody PasswordChangeRequest req) {
        User user = (User) authentication.getPrincipal();
        String email = user.getEmail();
        userService.changePassword(email, req);
        return ResponseEntity.ok(ApiResponse.ok(MessageConstants.PASSWORD_CHANGED));
    }

    @Operation(summary = "Kullanıcı hesabını siler")
    @DeleteMapping("/me")
    public ResponseEntity<ApiResponse<Void>> deleteMe(Authentication authentication) {
        User user = (User) authentication.getPrincipal();
        String email = user.getEmail();
        userService.deleteMe(email);
        return ResponseEntity.ok(ApiResponse.ok(MessageConstants.ACCOUNT_DELETED));
    }

    @Operation(summary = "Tüm cihazlardan çıkış yapar")
    @PostMapping("/me/logout-all")
    public ResponseEntity<ApiResponse<Void>> logoutAllDevices(Authentication authentication) {
        User user = (User) authentication.getPrincipal();
        String email = user.getEmail();
        userService.logoutAllDevices(email);
        return ResponseEntity.ok(ApiResponse.ok("Tüm cihazlardan çıkış yapıldı"));
    }

    @Operation(summary = "Email değişikliği talebinde bulunur")
    @PostMapping("/me/change-email")
    public ResponseEntity<ApiResponse<Void>> requestEmailChange(
            Authentication authentication,
            @Valid @RequestBody EmailChangeRequest req) {
        User user = (User) authentication.getPrincipal();
        String email = user.getEmail();
        userService.requestEmailChange(email, req);
        return ResponseEntity
                .ok(ApiResponse.ok("Email değişikliği talebi oluşturuldu. Lütfen yeni email adresinizi kontrol edin."));
    }

    @Operation(summary = "Email değişikliğini onaylar")
    @GetMapping("/confirm-email-change/{token}")
    public ResponseEntity<ApiResponse<Void>> confirmEmailChange(@PathVariable String token) {
        userService.confirmEmailChange(token);
        return ResponseEntity.ok(ApiResponse.ok("Email adresiniz başarıyla güncellendi."));
    }

    // ========== Profile Image ==========

    @Operation(summary = "Profil resmi yükler")
    @PostMapping(value = "/me/profile-image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<UserResponse>> uploadProfileImage(
            Authentication authentication,
            @RequestParam("file") MultipartFile file) {
        User user = (User) authentication.getPrincipal();
        String userId = user.getId().toString();
        String email = user.getEmail();

        // Upload to S3
        String imageUrl = fileUploadService.uploadProfileImage(userId, file);

        // Update user profile
        UserResponse res = userService.updateProfileImage(email, imageUrl);

        return ResponseEntity.ok(ApiResponse.ok("Profil resmi yüklendi", res));
    }

    @Operation(summary = "Profil resmini siler")
    @DeleteMapping("/me/profile-image")
    public ResponseEntity<ApiResponse<UserResponse>> deleteProfileImage(Authentication authentication) {
        User user = (User) authentication.getPrincipal();
        String email = user.getEmail();

        UserResponse res = userService.deleteProfileImage(email);

        return ResponseEntity.ok(ApiResponse.ok("Profil resmi silindi", res));
    }
}
