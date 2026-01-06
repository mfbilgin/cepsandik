package com.cepsandik.userservice.repositories;

import com.cepsandik.userservice.models.PlatformRole;
import com.cepsandik.userservice.models.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {
    Optional<User> findByEmail(String email);

    Optional<User> findByVerificationToken(String token);

    @Query(value = "SELECT * FROM users WHERE email = :email", nativeQuery = true)
    Optional<User> findAnyByEmail(String email);

    // Admin statistics
    long countByIsActiveTrue();

    long countByIsVerifiedTrue();

    long countByPlatformRole(PlatformRole role);

    @Query(value = "SELECT COUNT(*) FROM users WHERE deleted_at IS NOT NULL", nativeQuery = true)
    long countByDeletedAtIsNotNull();

    long countByCreatedAtAfter(LocalDateTime dateTime);
}
