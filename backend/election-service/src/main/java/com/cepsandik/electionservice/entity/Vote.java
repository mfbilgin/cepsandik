package com.cepsandik.electionservice.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "votes", uniqueConstraints = {
    @UniqueConstraint(name = "uk_vote_token", columnNames = {"vote_token"}),
    @UniqueConstraint(name = "uk_vote_election_token", columnNames = {"election_id", "vote_token"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Vote {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "election_id", nullable = false)
    private Election election;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "candidate_id", nullable = false)
    private Candidate candidate;

    /** Oy kullanmak için kullanılan tek kullanımlık token */
    @Column(name = "vote_token", nullable = false, length = 36)
    private String voteToken;

    /** Şifreli oy verisi (ElectionGuard CiphertextBallot JSON) */
    @Column(name = "encrypted_ballot", columnDefinition = "TEXT")
    private String encryptedBallot;

    /** ElectionGuard tracking code (doğrulama kodu) */
    @Column(name = "tracking_code", length = 128)
    private String trackingCode;

    /** Zero Knowledge Proof (JSON) */
    @Column(name = "zkp_proof", columnDefinition = "TEXT")
    private String zkpProof;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
