package com.cepsandik.electionservice.entity;

import com.cepsandik.electionservice.enums.ElectionStatus;
import com.cepsandik.electionservice.enums.ElectionType;
import com.cepsandik.electionservice.enums.ParticipantType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
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

    /** Seçim başlangıç anı (UTC, DB: timestamptz) */
    @Column(name = "start_time")
    private Instant startTime;

    /** Seçim bitiş anı (UTC, DB: timestamptz) */
    @Column(name = "end_time")
    private Instant endTime;

    /** Sonuçların herkese açık olup olmadığı */
    @Column(name = "results_public")
    @Builder.Default
    private Boolean resultsPublic = true;

    /** Soft delete */
    @Column(name = "is_deleted")
    @Builder.Default
    private Boolean isDeleted = false;

    // === ElectionGuard Kriptografik Alanlar ===

    /** ElectionGuard CiphertextElectionContext (JSON) */
    @Column(name = "election_guard_context", columnDefinition = "TEXT")
    private String electionGuardContext;

    /** ElectionGuard Manifest (JSON) */
    @Column(name = "election_manifest", columnDefinition = "TEXT")
    private String electionManifest;

    /** ElGamal joint public key */
    @Column(name = "election_public_key", columnDefinition = "TEXT")
    private String electionPublicKey;

    /** Dağıtık emanetçi kayıtları (JSON) */
    @Column(name = "guardian_records", columnDefinition = "TEXT")
    private String guardianRecords;

    /** Minimum emanetçi (Q) eşik değeri */
    @Column(name = "min_guardians_threshold")
    @Builder.Default
    private Integer minGuardiansThreshold = 3;

    /** ElectionGuard Tally Decryption Proof (JSON) */
    @Column(name = "tally_proof", columnDefinition = "TEXT")
    private String tallyProof;

    /** Normalized crypto tally results returned by Crypto-Engine (JSON). */
    @Column(name = "tally_results", columnDefinition = "TEXT")
    private String tallyResults;

    /** Distributed tally decryption session ID (Sprint 5.C.2.5). */
    @Column(name = "tally_session_id", length = 64)
    private String tallySessionId;

    /** EncryptedTally JSON (Sprint 5.C.2.5) — mobile guardian pull eder. */
    @Column(name = "encrypted_tally_json", columnDefinition = "TEXT")
    private String encryptedTallyJson;

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
