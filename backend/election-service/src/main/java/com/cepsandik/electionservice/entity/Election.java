package com.cepsandik.electionservice.entity;

import com.cepsandik.electionservice.enums.ElectionStatus;
import com.cepsandik.electionservice.enums.ElectionType;
import com.cepsandik.electionservice.enums.ParticipantType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "elections")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Election {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    /** Topluluğa bağlı seçim (null ise bağımsız seçim) */
    @Column(name = "community_id")
    private Long communityId;

    /** Seçimi oluşturan kullanıcı */
    @Column(name = "created_by", nullable = false)
    private String createdBy;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private ElectionStatus status = ElectionStatus.DRAFT;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private ElectionType type = ElectionType.SINGLE_CHOICE;

    @Enumerated(EnumType.STRING)
    @Column(name = "participant_type", nullable = false)
    @Builder.Default
    private ParticipantType participantType = ParticipantType.ALL_MEMBERS;

    /** Çoklu seçimde maksimum seçilebilecek aday sayısı */
    @Column(name = "max_selections")
    private Integer maxSelections;

    /** Seçim başlangıç zamanı */
    @Column(name = "start_time")
    private LocalDateTime startTime;

    /** Seçim bitiş zamanı */
    @Column(name = "end_time")
    private LocalDateTime endTime;

    /** Sonuçların herkese açık olup olmadığı */
    @Column(name = "results_public")
    @Builder.Default
    private Boolean resultsPublic = false;

    /** Anonim oylama (kim neye oy verdi gizli) */
    @Column(name = "anonymous_voting")
    @Builder.Default
    private Boolean anonymousVoting = true;

    /** Soft delete */
    @Column(name = "is_deleted")
    @Builder.Default
    private Boolean isDeleted = false;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    /** Adaylar/Seçenekler */
    @OneToMany(mappedBy = "election", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<Candidate> candidates = new ArrayList<>();

    /** Erişim kodları (bağımsız seçimler için) */
    @OneToMany(mappedBy = "election", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<AccessCode> accessCodes = new ArrayList<>();

    // Helper methods
    public void addCandidate(Candidate candidate) {
        candidates.add(candidate);
        candidate.setElection(this);
    }

    public void removeCandidate(Candidate candidate) {
        candidates.remove(candidate);
        candidate.setElection(null);
    }

    public boolean isDraft() {
        return status == ElectionStatus.DRAFT;
    }

    public boolean isScheduled() {
        return status == ElectionStatus.SCHEDULED;
    }

    public boolean isActive() {
        return status == ElectionStatus.ACTIVE;
    }

    public boolean isClosed() {
        return status == ElectionStatus.CLOSED;
    }

    public boolean isEditable() {
        return status == ElectionStatus.DRAFT;
    }
}
