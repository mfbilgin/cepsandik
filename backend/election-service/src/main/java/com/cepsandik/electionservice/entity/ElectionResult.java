package com.cepsandik.electionservice.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "election_results", uniqueConstraints = {
    @UniqueConstraint(name = "idx_election_results_election_candidate", columnNames = {"election_id", "candidate_id"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ElectionResult {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "election_id", nullable = false)
    private Election election;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "candidate_id", nullable = false)
    private Candidate candidate;

    /** Adayın aldığı oy sayısı */
    @Column(name = "vote_count", nullable = false)
    @Builder.Default
    private Long voteCount = 0L;

    /** Oy yüzdesi */
    @Column(nullable = false)
    @Builder.Default
    private Double percentage = 0.0;

    /** Sıralama (1 = birinci) */
    @Column(nullable = false)
    @Builder.Default
    private Integer rank = 0;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
