package com.cepsandik.userservice.models;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Two-Factor Authentication entity.
 * Stores TOTP secret and backup codes for users.
 */
@Entity
@Table(name = "two_factor_auth")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TwoFactorAuth {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    /**
     * TOTP secret key (Base32 encoded)
     */
    @Column(name = "secret_key", nullable = false)
    private String secretKey;

    /**
     * Whether 2FA is enabled and verified
     */
    @Column(name = "enabled", nullable = false)
    @Builder.Default
    private boolean enabled = false;

    /**
     * Backup codes (comma-separated, hashed)
     */
    @Column(name = "backup_codes", columnDefinition = "TEXT")
    private String backupCodes;

    @org.hibernate.annotations.CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "verified_at")
    private LocalDateTime verifiedAt;
}
