package com.cepsandik.electionservice.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

@Entity
@Table(name = "votes", uniqueConstraints = {
    @UniqueConstraint(name = "uk_vote_election_ballot", columnNames = {"election_id", "ballot_id"}),
    @UniqueConstraint(name = "uk_vote_election_tracking", columnNames = {"election_id", "tracking_code"})
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

    /** Anonymous ElectionGuard ballot id. It must not encode user or option data. */
    @Column(name = "ballot_id", nullable = false, length = 128)
    private String ballotId;

    /** ElectionGuard CiphertextBallot JSON. */
    @Column(name = "encrypted_ballot", nullable = false, columnDefinition = "TEXT")
    private String encryptedBallot;

    /** ElectionGuard tracking code exposed to the voter for recorded-as-cast checks. */
    @Column(name = "tracking_code", nullable = false, length = 128)
    private String trackingCode;

    /** Ballot encryption proof metadata / verifier payload. */
    @Column(name = "zkp_proof", nullable = false, columnDefinition = "TEXT")
    private String zkpProof;

    /** SHA-256 hash of encryptedBallot, used by the bulletin board hash chain. */
    @Column(name = "ballot_hash", nullable = false, length = 64)
    private String ballotHash;

    @CreationTimestamp
    @Column(name = "cast_at", nullable = false, updatable = false)
    private Instant castAt;
}
