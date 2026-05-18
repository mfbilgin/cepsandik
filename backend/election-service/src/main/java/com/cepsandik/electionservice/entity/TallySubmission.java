package com.cepsandik.electionservice.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;

/**
 * Faz 2.6 — Kalıcı tally submission (durable replay).
 *
 * Guardian'ın crypto-engine'e gönderdiği partial decryption / challenge
 * response JSON'ı. crypto-engine session'ı kaybolursa election-service yeni
 * session açıp bunları SIRAYLA replay eder; guardian (mobil) yeniden hesap
 * yapmaz. (election_id, guardian_id, round) tekildir — tekrar gönderim upsert.
 */
@Entity
@Table(name = "tally_submissions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TallySubmission {

    public static final String ROUND_PARTIAL = "PARTIAL";
    public static final String ROUND_CHALLENGE_RESPONSE = "CHALLENGE_RESPONSE";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "election_id", nullable = false)
    private Long electionId;

    @Column(name = "guardian_id", nullable = false, length = 64)
    private String guardianId;

    @Column(name = "round", nullable = false, length = 32)
    private String round;

    @Column(name = "payload_json", columnDefinition = "TEXT", nullable = false)
    private String payloadJson;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
