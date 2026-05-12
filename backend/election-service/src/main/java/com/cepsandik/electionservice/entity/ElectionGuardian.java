package com.cepsandik.electionservice.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "election_guardians")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ElectionGuardian {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "election_id", nullable = false)
    private Election election;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    /** EG 2.1 Kamu Anahtarı (ElementModP hex) */
    @Column(name = "public_key", columnDefinition = "TEXT")
    private String publicKey;

    /** EG 2.1 Taahhütler (Commitments JSON) */
    @Column(name = "commitments", columnDefinition = "TEXT")
    private String commitments;

    /** Seremoni Yedek Payları (Backups JSON) */
    @Column(name = "key_backups", columnDefinition = "TEXT")
    private String keyBackups;

    /** EG 2.1 Schnorr Proofs (ZKP JSON) */
    @Column(name = "coefficient_proofs", columnDefinition = "TEXT")
    private String coefficientProofs;

    /** Deşifre Payı (Sonuç açıklama aşamasında) */
    @Column(name = "decryption_share", columnDefinition = "TEXT")
    private String decryptionShare;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private GuardianStatus status = GuardianStatus.PENDING;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public enum GuardianStatus {
        PENDING,        // Davet edildi/seçildi
        KEY_UPLOADED,   // Kamu anahtarı ve taahhütler yüklendi
        SHARE_UPLOADED  // Deşifre payı yüklendi
    }
}
