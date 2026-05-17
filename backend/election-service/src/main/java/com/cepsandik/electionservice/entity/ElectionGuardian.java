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

    /** KMP polinom x koordinatı (1-indexed). Guardian sırası yerine açık kolon. */
    @Column(name = "x_coordinate")
    private Integer xCoordinate;

    /** Asıl guardian mı yedek mi (Sprint 5.A.distributed STATE_RESET). */
    @Column(name = "is_backup", nullable = false)
    @Builder.Default
    private boolean isBackup = false;

    /** Yedek listesindeki sıra (is_backup=true ise). Düşük = önce devreye girer. */
    @Column(name = "backup_order")
    private Integer backupOrder;

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
        PENDING,         // Davet edildi/seçildi, henüz aksiyon yok
        KEY_UPLOADED,    // Round 1: public key + Schnorr proof yüklendi
        KEYS_EXCHANGED,  // Round 2: peer'lara encrypted key share üretildi/relay'lendi
        READY,           // Round 3: ceremony finalize, trustee state cihazda — ceremony-complete ack
        DECLINED,        // Guardian "bu seçime katılmıyorum" dedi (cezasız)
        TIMEOUT,         // Süre doldu, aksiyon yok (abandonment — SUSPENDED_30D tetikler)
        SHARE_UPLOADED   // Tally aşaması: partial decryption gönderildi (distributed tally)
    }
}
