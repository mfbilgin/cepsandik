package com.cepsandik.electionservice.dto.response;

import com.cepsandik.electionservice.enums.ElectionStatus;
import com.cepsandik.electionservice.enums.ElectionType;
import com.cepsandik.electionservice.enums.ParticipantType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ElectionResponse {

    private Long id;
    private String title;
    private String description;
    private Long communityId;
    private String createdBy;
    private ElectionStatus status;
    private ElectionType type;
    private ParticipantType participantType;
    private Integer maxSelections;
    private Instant startTime;
    private Instant endTime;
    private Boolean resultsPublic;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    
    /** Aday sayısı */
    private Integer candidateCount;
    
    /** Adaylar (detaylı görünümde) */
    private List<CandidateResponse> candidates;

    /** ElectionGuard kurulduysa true — mobil oy için transit RSA şifrelemesi kullanılmalı */
    private Boolean encryptedVotingEnabled;

    /** ElectionGuard Election Manifest (JSON) — Bağımsız denetim için */
    private String electionManifest;
}
