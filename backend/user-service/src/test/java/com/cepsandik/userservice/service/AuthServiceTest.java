package com.cepsandik.userservice.service;

import com.cepsandik.userservice.common.MessageConstants;
import com.cepsandik.userservice.dtos.requests.LoginRequest;
import com.cepsandik.userservice.dtos.requests.RegisterRequest;
import com.cepsandik.userservice.dtos.responses.AuthResponse;
import com.cepsandik.userservice.dtos.responses.UserResponse;
import com.cepsandik.userservice.exceptions.ApiException;
import com.cepsandik.userservice.models.PlatformRole;
import com.cepsandik.userservice.models.RefreshToken;
import com.cepsandik.userservice.models.User;
import com.cepsandik.userservice.repositories.PasswordResetTokenRepository;
import com.cepsandik.userservice.repositories.RefreshTokenRepository;
import com.cepsandik.userservice.repositories.UserRepository;
import com.cepsandik.userservice.security.JwtService;
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

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("AuthService Unit Tests")
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private RefreshTokenRepository refreshRepository;

    @Mock
    private PasswordResetTokenRepository resetRepository;

    @Mock
    private PasswordEncoder encoder;

    @Mock
    private JwtService jwt;

    @Mock
    private EmailProducer emailProducer;

    @Mock
    private LoginAttemptService loginAttemptService;

    @Mock
    private TwoFactorAuthService twoFactorAuthService;

    @InjectMocks
    private AuthService authService;

    private User testUser;
    private RegisterRequest registerRequest;
    private LoginRequest loginRequest;

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

        registerRequest = new RegisterRequest(
                "Test",
                "User",
                "test@example.com",
                "Password123!");

        loginRequest = new LoginRequest(
                "test@example.com",
                "Password123!");
    }

    @Nested
    @DisplayName("Register Tests")
    class RegisterTests {

        @Test
        @DisplayName("Should register user successfully")
        void shouldRegisterUserSuccessfully() {
            // Given
            when(userRepository.findAnyByEmail(anyString())).thenReturn(Optional.empty());
            when(encoder.encode(anyString())).thenReturn("hashedPassword");
            when(userRepository.save(any(User.class))).thenAnswer(i -> {
                User user = i.getArgument(0);
                user.setId(UUID.randomUUID());
                return user;
            });

            // When
            UserResponse response = authService.register(registerRequest);

            // Then
            assertThat(response).isNotNull();
            assertThat(response.email()).isEqualTo("test@example.com");
            verify(emailProducer).sendVerificationEmail(anyString(), anyString(), anyString());
        }

        @Test
        @DisplayName("Should throw error when email already exists")
        void shouldThrowErrorWhenEmailExists() {
            // Given
            when(userRepository.findAnyByEmail(anyString())).thenReturn(Optional.of(testUser));

            // When/Then
            assertThatThrownBy(() -> authService.register(registerRequest))
                    .isInstanceOf(ApiException.class)
                    .hasFieldOrPropertyWithValue("status", HttpStatus.CONFLICT);
        }
    }

    @Nested
    @DisplayName("Login Tests")
    class LoginTests {

        @Test
        @DisplayName("Should login successfully without 2FA")
        void shouldLoginSuccessfully() {
            // Given
            when(loginAttemptService.isBlocked(anyString())).thenReturn(false);
            when(userRepository.findAnyByEmail(anyString())).thenReturn(Optional.of(testUser));
            when(encoder.matches(anyString(), anyString())).thenReturn(true);
            when(twoFactorAuthService.isTwoFactorEnabled(any(UUID.class))).thenReturn(false);
            when(jwt.generateAccessTokenWithClaims(any(User.class)))
                    .thenReturn(Map.of("token", "accessToken", "expiration", 123456789L));
            when(refreshRepository.save(any(RefreshToken.class)))
                    .thenReturn(RefreshToken.builder().token("refreshToken").build());

            // When
            AuthResponse response = authService.login(loginRequest);

            // Then
            assertThat(response).isNotNull();
            assertThat(response.accessToken()).isEqualTo("accessToken");
            assertThat(response.requires2FA()).isFalse();
            verify(loginAttemptService).clearAttempts(anyString());
        }

        @Test
        @DisplayName("Should return requires2FA when 2FA is enabled")
        void shouldReturnRequires2FAWhenEnabled() {
            // Given
            when(loginAttemptService.isBlocked(anyString())).thenReturn(false);
            when(userRepository.findAnyByEmail(anyString())).thenReturn(Optional.of(testUser));
            when(encoder.matches(anyString(), anyString())).thenReturn(true);
            when(twoFactorAuthService.isTwoFactorEnabled(any(UUID.class))).thenReturn(true);
            when(jwt.generateTempToken(any(User.class))).thenReturn("tempToken");

            // When
            AuthResponse response = authService.login(loginRequest);

            // Then
            assertThat(response).isNotNull();
            assertThat(response.requires2FA()).isTrue();
            assertThat(response.tempToken()).isEqualTo("tempToken");
            assertThat(response.accessToken()).isNull();
        }

        @Test
        @DisplayName("Should throw error for invalid credentials")
        void shouldThrowErrorForInvalidCredentials() {
            // Given
            when(loginAttemptService.isBlocked(anyString())).thenReturn(false);
            when(userRepository.findAnyByEmail(anyString())).thenReturn(Optional.of(testUser));
            when(encoder.matches(anyString(), anyString())).thenReturn(false);

            // When/Then
            assertThatThrownBy(() -> authService.login(loginRequest))
                    .isInstanceOf(ApiException.class)
                    .hasFieldOrPropertyWithValue("status", HttpStatus.UNAUTHORIZED);

            verify(loginAttemptService).recordFailedAttempt(anyString());
        }

        @Test
        @DisplayName("Should throw error when account is blocked")
        void shouldThrowErrorWhenAccountBlocked() {
            // Given
            when(loginAttemptService.isBlocked(anyString())).thenReturn(true);
            when(loginAttemptService.getRemainingLockoutMinutes(anyString())).thenReturn(10L);

            // When/Then
            assertThatThrownBy(() -> authService.login(loginRequest))
                    .isInstanceOf(ApiException.class)
                    .hasFieldOrPropertyWithValue("status", HttpStatus.TOO_MANY_REQUESTS);
        }

        @Test
        @DisplayName("Should throw error for unverified email")
        void shouldThrowErrorForUnverifiedEmail() {
            // Given
            testUser.setVerified(false);
            when(loginAttemptService.isBlocked(anyString())).thenReturn(false);
            when(userRepository.findAnyByEmail(anyString())).thenReturn(Optional.of(testUser));
            when(encoder.matches(anyString(), anyString())).thenReturn(true);

            // When/Then
            assertThatThrownBy(() -> authService.login(loginRequest))
                    .isInstanceOf(ApiException.class)
                    .hasFieldOrPropertyWithValue("status", HttpStatus.FORBIDDEN);
        }
    }

    @Nested
    @DisplayName("Email Verification Tests")
    class VerifyEmailTests {

        @Test
        @DisplayName("Should verify email successfully")
        void shouldVerifyEmailSuccessfully() {
            // Given
            testUser.setVerified(false);
            testUser.setVerificationToken("validToken");
            when(userRepository.findByVerificationToken("validToken")).thenReturn(Optional.of(testUser));

            // When
            String result = authService.verifyEmail("validToken");

            // Then
            assertThat(result).isEqualTo(MessageConstants.ACCOUNT_VERIFIED);
            assertThat(testUser.isVerified()).isTrue();
            verify(userRepository).save(testUser);
        }

        @Test
        @DisplayName("Should throw error for invalid verification token")
        void shouldThrowErrorForInvalidToken() {
            // Given
            when(userRepository.findByVerificationToken("invalidToken")).thenReturn(Optional.empty());

            // When/Then
            assertThatThrownBy(() -> authService.verifyEmail("invalidToken"))
                    .isInstanceOf(ApiException.class)
                    .hasFieldOrPropertyWithValue("status", HttpStatus.NOT_FOUND);
        }
    }
}
