package com.cepsandik.userservice.service;

import com.cepsandik.userservice.dtos.requests.PasswordChangeRequest;
import com.cepsandik.userservice.dtos.requests.UpdateProfileRequest;
import com.cepsandik.userservice.dtos.responses.UserResponse;
import com.cepsandik.userservice.exceptions.ApiException;
import com.cepsandik.userservice.models.PlatformRole;
import com.cepsandik.userservice.models.User;
import com.cepsandik.userservice.repositories.EmailChangeTokenRepository;
import com.cepsandik.userservice.repositories.RefreshTokenRepository;
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
@DisplayName("UserService Unit Tests")
class UserServiceTest {

    @Mock
    private UserRepository userRepo;

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @Mock
    private EmailChangeTokenRepository emailChangeTokenRepository;

    @Mock
    private PasswordEncoder encoder;

    @Mock
    private EmailService emailService;

    @InjectMocks
    private UserService userService;

    private User testUser;

    @BeforeEach
    void setUp() {
        testUser = User.builder()
                .id(UUID.randomUUID())
                .firstName("John")
                .lastName("Doe")
                .email("john@example.com")
                .passwordHash("hashedPassword")
                .isActive(true)
                .isVerified(true)
                .platformRole(PlatformRole.USER)
                .build();
    }

    @Nested
    @DisplayName("Profile Tests")
    class ProfileTests {

        @Test
        @DisplayName("Should get user profile successfully")
        void shouldGetProfileSuccessfully() {
            // Given
            when(userRepo.findByEmail("john@example.com")).thenReturn(Optional.of(testUser));

            // When
            UserResponse response = userService.me("john@example.com");

            // Then
            assertThat(response).isNotNull();
            assertThat(response.email()).isEqualTo("john@example.com");
            assertThat(response.firstName()).isEqualTo("John");
        }

        @Test
        @DisplayName("Should throw error for non-existent user")
        void shouldThrowErrorForNonExistentUser() {
            // Given
            when(userRepo.findByEmail("unknown@example.com")).thenReturn(Optional.empty());

            // When/Then
            assertThatThrownBy(() -> userService.me("unknown@example.com"))
                    .isInstanceOf(ApiException.class)
                    .hasFieldOrPropertyWithValue("status", HttpStatus.NOT_FOUND);
        }

        @Test
        @DisplayName("Should update profile successfully")
        void shouldUpdateProfileSuccessfully() {
            // Given
            when(userRepo.findByEmail("john@example.com")).thenReturn(Optional.of(testUser));
            when(userRepo.save(any(User.class))).thenReturn(testUser);

            UpdateProfileRequest request = new UpdateProfileRequest("Jane", "Smith");

            // When
            UserResponse response = userService.updateMe("john@example.com", request);

            // Then
            assertThat(response).isNotNull();
            verify(userRepo).save(any(User.class));
        }
    }

    @Nested
    @DisplayName("Password Change Tests")
    class PasswordChangeTests {

        @Test
        @DisplayName("Should change password successfully")
        void shouldChangePasswordSuccessfully() {
            // Given
            when(userRepo.findByEmail("john@example.com")).thenReturn(Optional.of(testUser));
            when(encoder.matches("oldPassword", "hashedPassword")).thenReturn(true);
            when(encoder.encode("newPassword")).thenReturn("newHashedPassword");

            PasswordChangeRequest request = new PasswordChangeRequest("oldPassword", "newPassword");

            // When
            userService.changePassword("john@example.com", request);

            // Then
            verify(userRepo).save(testUser);
            assertThat(testUser.getPasswordHash()).isEqualTo("newHashedPassword");
        }

        @Test
        @DisplayName("Should throw error for incorrect current password")
        void shouldThrowErrorForIncorrectPassword() {
            // Given
            when(userRepo.findByEmail("john@example.com")).thenReturn(Optional.of(testUser));
            when(encoder.matches("wrongPassword", "hashedPassword")).thenReturn(false);

            PasswordChangeRequest request = new PasswordChangeRequest("wrongPassword", "newPassword");

            // When/Then
            assertThatThrownBy(() -> userService.changePassword("john@example.com", request))
                    .isInstanceOf(ApiException.class)
                    .hasFieldOrPropertyWithValue("status", HttpStatus.UNAUTHORIZED);
        }
    }

    @Nested
    @DisplayName("Account Deletion Tests")
    class AccountDeletionTests {

        @Test
        @DisplayName("Should delete account (soft delete)")
        void shouldDeleteAccountSuccessfully() {
            // Given
            when(userRepo.findByEmail("john@example.com")).thenReturn(Optional.of(testUser));

            // When
            userService.deleteMe("john@example.com");

            // Then
            verify(userRepo).save(testUser);
            assertThat(testUser.isActive()).isFalse();
            assertThat(testUser.getDeletedAt()).isNotNull();
        }
    }

    @Nested
    @DisplayName("Profile Image Tests")
    class ProfileImageTests {

        @Test
        @DisplayName("Should update profile image")
        void shouldUpdateProfileImage() {
            // Given
            when(userRepo.findByEmail("john@example.com")).thenReturn(Optional.of(testUser));
            when(userRepo.save(any(User.class))).thenReturn(testUser);

            // When
            UserResponse response = userService.updateProfileImage("john@example.com",
                    "https://s3.example.com/image.jpg");

            // Then
            assertThat(response).isNotNull();
            verify(userRepo).save(testUser);
        }

        @Test
        @DisplayName("Should delete profile image")
        void shouldDeleteProfileImage() {
            // Given
            testUser.setProfileImage("https://s3.example.com/old-image.jpg");
            when(userRepo.findByEmail("john@example.com")).thenReturn(Optional.of(testUser));
            when(userRepo.save(any(User.class))).thenReturn(testUser);

            // When
            UserResponse response = userService.deleteProfileImage("john@example.com");

            // Then
            assertThat(response).isNotNull();
            verify(userRepo).save(testUser);
        }
    }

    @Nested
    @DisplayName("Logout All Devices Tests")
    class LogoutAllDevicesTests {

        @Test
        @DisplayName("Should logout from all devices")
        void shouldLogoutFromAllDevices() {
            // Given
            when(userRepo.findByEmail("john@example.com")).thenReturn(Optional.of(testUser));

            // When
            userService.logoutAllDevices("john@example.com");

            // Then
            verify(refreshTokenRepository).deleteByUserId(testUser.getId());
        }
    }
}
