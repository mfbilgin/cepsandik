package com.cepsandik.electionservice.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

/**
 * Faz 1.1 — Transactional outbox satırı.
 *
 * Ceremony/tally state değişikliğiyle AYNI @Transactional içinde kaydedilir
 * (atomik). {@code BulletinOutboxPublisher} bunu sonradan bulletin-board
 * servisine SIRAYLA, retry'lı, idempotent teslim eder. Böylece şeffaflık
 * kaydı ne sessizce kaybolur ne de dış çağrı ceremony tx'ini rollback eder.
 */
@Entity
@Table(name = "bulletin_outbox")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BulletinOutbox {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Bulletin-board'a gönderilen idempotency anahtarı (retry'da çift kayıt olmaz). */
    @Column(name = "idempotency_key", nullable = false, unique = true)
    private UUID idempotencyKey;

    @Column(name = "election_id", nullable = false, length = 64)
    private String electionId;

    @Column(name = "record_type", nullable = false, length = 64)
    private String recordType;

    @Column(name = "tracking_code", length = 128)
    private String trackingCode;

    @Column(name = "ballot_hash", length = 256)
    private String ballotHash;

    @Column(name = "payload", columnDefinition = "TEXT")
    private String payload;

    /**
     * true ise bu kayıt verifiability için zorunlu (PUBLIC_KEYS,
     * JOINT_KEY_GENERATED, BALLOT_CAST, TALLY_PUBLISHED). Yayımlanmamış kritik
     * kayıt varken election arşivlenemez / sonuç publish edilemez.
     */
    @Column(name = "critical", nullable = false)
    @Builder.Default
    private boolean critical = false;

    @Column(name = "published", nullable = false)
    @Builder.Default
    private boolean published = false;

    @Column(name = "attempts", nullable = false)
    @Builder.Default
    private int attempts = 0;

    @Column(name = "last_error", columnDefinition = "TEXT")
    private String lastError;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "published_at")
    private Instant publishedAt;
}
