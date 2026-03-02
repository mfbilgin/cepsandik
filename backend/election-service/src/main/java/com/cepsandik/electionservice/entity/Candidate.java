package com.cepsandik.electionservice.entity;

import com.cepsandik.electionservice.enums.CandidateType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "candidates")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Candidate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "election_id", nullable = false)
    private Election election;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    /**
     * Aday görseli URL (PERSON type için topluluk üyesinin avatarından otomatik
     * gelir)
     */
    @Column(name = "image_url")
    private String imageUrl;

    /**
     * Aday türü:
     * PERSON → topluluk üyelerinden seçilir; ad ve avatar otomatik döner
     * TEXT_OPTION → serbest metin seçeneği
     * IMAGE_OPTION → görsel tabanlı seçenek
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "candidate_type", nullable = false, length = 20)
    @Builder.Default
    private CandidateType candidateType = CandidateType.PERSON;

    /**
     * PERSON type için referans üye user ID (topluluk üzyesinin ad/avatar bilgisi
     * UI'da buradan çekilir)
     */
    @Column(name = "member_user_id")
    private String memberUserId;

    /** Sıralama (görüntüleme sırası) */
    @Column(name = "display_order")
    @Builder.Default
    private Integer displayOrder = 0;

    /** Soft delete */
    @Column(name = "is_deleted")
    @Builder.Default
    private Boolean isDeleted = false;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
