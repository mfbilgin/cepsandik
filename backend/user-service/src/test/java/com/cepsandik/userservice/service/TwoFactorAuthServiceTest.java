package com.cepsandik.userservice.service;

import com.cepsandik.userservice.dtos.responses.TwoFactorSetupResponse;
import com.cepsandik.userservice.exceptions.ApiException;
import com.cepsandik.userservice.models.PlatformRole;
import com.cepsandik.userservice.models.TwoFactorAuth;
import com.cepsandik.userservice.models.User;
import com.cepsandik.userservice.repositories.TwoFactorAuthRepository;
import com.cepsandik.userservice.repositories.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("TwoFactorAuthService Unit Tests")
class TwoFactorAuthServiceTest {

    @Mock
    private TwoFactorAuthRepository twoFactorAuthRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private TwoFactorAuthService twoFactorAuthService;

    private User testUser;

    @BeforeEach
    void setUp() {
        testUser = User.builder()
                .id(UUID.randomUUID())
                .firstName("Test")
                .lastName("User")
                .email("test@example.com")
                .passwordHash("hashedPassword")
                .isActive(true)
                .isVerified(true)
                .platformRole(PlatformRole.USER)
                .build();
    }

    @Nested
    @DisplayName("Setup 2FA Tests")
    class SetupTests {

        @Test
        @DisplayName("Should setup 2FA successfully")
        void shouldSetup2FASuccessfully() {
            // Given
            when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(testUser));
            when(twoFactorAuthRepository.findByUserId(any(UUID.class))).thenReturn(Optional.empty());
            when(passwordEncoder.encode(anyString())).thenReturn("hashedCode");
            when(twoFactorAuthRepository.save(any(TwoFactorAuth.class))).thenAnswer(i -> i.getArgument(0));

            // When
            TwoFactorSetupResponse response = twoFactorAuthService.setupTwoFactor("test@example.com");

            // Then
            assertThat(response).isNotNull();
            assertThat(response.secretKey()).isNotNull();
            assertThat(response.qrCodeUri()).startsWith("data:image/png;base64,");
            assertThat(response.backupCodes()).hasSize(8);
            verify(twoFactorAuthRepository).save(any(TwoFactorAuth.class));
        }

        @Test
        @DisplayName("Should throw error if 2FA already enabled")
        void shouldThrowErrorIf2FAAlreadyEnabled() {
            // Given
            TwoFactorAuth existing2FA = TwoFactorAuth.builder()
                    .user(testUser)
                    .enabled(true)
                    .build();
            when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(testUser));
            when(twoFactorAuthRepository.findByUserId(any(UUID.class))).thenReturn(Optional.of(existing2FA));

            // When/Then
            assertThatThrownBy(() -> twoFactorAuthService.setupTwoFactor("test@example.com"))
                    .isInstanceOf(ApiException.class)
                    .hasFieldOrPropertyWithValue("status", HttpStatus.CONFLICT);
        }
    }

    @Nested
    @DisplayName("Check 2FA Status Tests")
    class StatusTests {

        @Test
        @DisplayName("Should return true when 2FA is enabled")
        void shouldReturnTrueWhen2FAEnabled() {
            // Given
            TwoFactorAuth twoFactorAuth = TwoFactorAuth.builder()
                    .user(testUser)
                    .enabled(true)
                    .build();
            when(twoFactorAuthRepository.findByUserId(testUser.getId())).thenReturn(Optional.of(twoFactorAuth));

            // When
            boolean result = twoFactorAuthService.isTwoFactorEnabled(testUser.getId());

            // Then
            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("Should return false when 2FA is not setup")
        void shouldReturnFalseWhen2FANotSetup() {
            // Given
            when(twoFactorAuthRepository.findByUserId(testUser.getId())).thenReturn(Optional.empty());

            // When
            boolean result = twoFactorAuthService.isTwoFactorEnabled(testUser.getId());

            // Then
            assertThat(result).isFalse();
        }
    }

    @Nested
    @DisplayName("Disable 2FA Tests")
    class DisableTests {

        @Test
        @DisplayName("Should disable 2FA with correct password")
        void shouldDisable2FASuccessfully() {
            // Given
            when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(testUser));
            when(passwordEncoder.matches("correctPassword", "hashedPassword")).thenReturn(true);

            // When
            twoFactorAuthService.disableTwoFactor("test@example.com", "correctPassword");

            // Then
            verify(twoFactorAuthRepository).deleteByUserId(testUser.getId());
        }

        @Test
        @DisplayName("Should throw error for incorrect password")
        void shouldThrowErrorForIncorrectPassword() {
            // Given
            when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(testUser));
            when(passwordEncoder.matches("wrongPassword", "hashedPassword")).thenReturn(false);

            // When/Then
            assertThatThrownBy(() -> twoFactorAuthService.disableTwoFactor("test@example.com", "wrongPassword"))
                    .isInstanceOf(ApiException.class)
                    .hasFieldOrPropertyWithValue("status", HttpStatus.UNAUTHORIZED);
        }
    }
}
