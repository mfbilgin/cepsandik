package com.cepsandik.electionservice.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "access_codes", indexes = {
    @Index(name = "idx_access_code", columnList = "code", unique = true)
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AccessCode {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "election_id", nullable = false)
    private Election election;

    /** 6 haneli alfanumerik kod (A-Z, 0-9) */
    @Column(nullable = false, unique = true, length = 6)
    private String code;

    /** Maksimum kullanım sayısı (null = sınırsız) */
    @Column(name = "max_uses")
    private Integer maxUses;

    /** Şu anki kullanım sayısı */
    @Column(name = "current_uses")
    @Builder.Default
    private Integer currentUses = 0;

    /** Oluşturan kullanıcı */
    @Column(name = "created_by", nullable = false)
    private String createdBy;

    /** Aktif mi */
    @Column(name = "is_active")
    @Builder.Default
    private Boolean isActive = true;

    /** Son kullanma tarihi (null = süresiz) */
    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    // Helper methods
    public boolean isValid() {
        if (!isActive) return false;
        if (expiresAt != null && expiresAt.isBefore(LocalDateTime.now())) return false;
        if (maxUses != null && currentUses >= maxUses) return false;
        return true;
    }

    public void incrementUsage() {
        this.currentUses++;
    }
}
