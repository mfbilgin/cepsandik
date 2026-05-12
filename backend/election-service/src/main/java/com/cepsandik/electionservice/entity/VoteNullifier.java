package com.cepsandik.electionservice.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

@Entity
@Table(name = "vote_nullifiers", uniqueConstraints = {
    @UniqueConstraint(name = "uk_vote_nullifier_election_hash", columnNames = {"election_id", "nullifier_hash"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VoteNullifier {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "election_id", nullable = false)
    private Election election;

    /** Anonymous double-vote prevention value; never stores user id or selected option. */
    @Column(name = "nullifier_hash", nullable = false, length = 128)
    private String nullifierHash;

    @Column(name = "ballot_id", nullable = false, length = 128)
    private String ballotId;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
