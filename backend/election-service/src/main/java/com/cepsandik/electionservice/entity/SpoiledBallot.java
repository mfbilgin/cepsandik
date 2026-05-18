package com.cepsandik.electionservice.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

/**
 * Faz 1.4 — Benaloh challenge ile spoil edilmiş ballot kaydı.
 *
 * Spoiled ballot ASLA tally'ye girmez. Nonce açıldığı için aynı ciphertext
 * (ballot_hash / tracking_code) bir daha cast EDİLEMEZ — castVote bu kayda
 * karşı kontrol eder (oy gizliliği koruması).
 */
@Entity
@Table(name = "spoiled_ballots")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SpoiledBallot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "election_id", nullable = false)
    private Long electionId;

    @Column(name = "ballot_id", length = 128)
    private String ballotId;

    @Column(name = "tracking_code", length = 128)
    private String trackingCode;

    @Column(name = "ballot_hash", length = 256)
    private String ballotHash;

    /** crypto-engine DecryptWithNonce trustless doğrulaması geçti mi. */
    @Column(name = "verified", nullable = false)
    @Builder.Default
    private boolean verified = false;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
