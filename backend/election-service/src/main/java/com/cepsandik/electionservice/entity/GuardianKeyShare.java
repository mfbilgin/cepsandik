package com.cepsandik.electionservice.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * Cross-trustee şifreli key share relay kaydı (Sprint 5.A.distributed).
 *
 * Sunucu içeriği GÖREMEZ: encrypted_share_json, alıcının public key'iyle
 * KMP HashedElGamal ile şifrelenmiş EncryptedKeyShareJson'dur. Sunucu sadece
 * from_guardian_id → to_guardian_id forward eder (opak relay).
 */
@Entity
@Table(name = "guardian_key_shares")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GuardianKeyShare {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "election_id", nullable = false)
    private Long electionId;

    /** KMP guardianId (örn. "trustee1") — share'i üreten. */
    @Column(name = "from_guardian_id", nullable = false, length = 64)
    private String fromGuardianId;

    /** KMP guardianId — share'in hedefi (sadece bu cihaz çözebilir). */
    @Column(name = "to_guardian_id", nullable = false, length = 64)
    private String toGuardianId;

    /** KMP EncryptedKeyShareJson — alıcının public key'iyle şifreli, opak. */
    @Column(name = "encrypted_share_json", nullable = false, columnDefinition = "TEXT")
    private String encryptedShareJson;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
