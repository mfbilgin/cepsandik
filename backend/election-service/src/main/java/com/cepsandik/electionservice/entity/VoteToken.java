package com.cepsandik.electionservice.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "vote_tokens", uniqueConstraints = {
    @UniqueConstraint(name = "uk_vote_token_election_user", columnNames = {"election_id", "user_id"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VoteToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "election_id", nullable = false)
    private Election election;

    /** Kullanıcı ID (user-service'den gelen UUID string) */
    @Column(name = "user_id", nullable = false)
    private String userId;

    /** Benzersiz tek kullanımlık token (UUID) */
    @Column(nullable = false, unique = true, length = 36)
    private String token;

    /** Token kullanıldı mı */
    @Column(name = "is_used")
    @Builder.Default
    private Boolean isUsed = false;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /** Token kullanılma zamanı */
    @Column(name = "used_at")
    private LocalDateTime usedAt;

    // Helper methods
    public boolean isValid() {
        return !isUsed;
    }

    public void markAsUsed() {
        this.isUsed = true;
        this.usedAt = LocalDateTime.now();
    }
}
