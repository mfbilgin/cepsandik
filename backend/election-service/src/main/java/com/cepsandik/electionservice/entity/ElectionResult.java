package com.cepsandik.electionservice.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "election_results", uniqueConstraints = {
    @UniqueConstraint(name = "uk_election_result_selection", columnNames = {"election_id", "selection_id"})
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

    /** Public ElectionGuard selection id, e.g. candidate_42. */
    @Column(name = "selection_id", nullable = false, length = 128)
    private String selectionId;

    @Column(name = "option_label", length = 200)
    private String optionLabel;

    @Column(name = "option_description", columnDefinition = "TEXT")
    private String optionDescription;

    @Column(name = "option_image_url", length = 500)
    private String optionImageUrl;

    /** Cryptographically decrypted tally for this selection. */
    @Column(name = "vote_count", nullable = false)
    @Builder.Default
    private Long voteCount = 0L;

    @Column(nullable = false)
    @Builder.Default
    private Double percentage = 0.0;

    @Column(nullable = false)
    @Builder.Default
    private Integer rank = 0;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
