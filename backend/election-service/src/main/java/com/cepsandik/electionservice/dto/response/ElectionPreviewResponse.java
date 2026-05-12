package com.cepsandik.electionservice.dto.response;

import com.cepsandik.electionservice.enums.ElectionStatus;
import com.cepsandik.electionservice.enums.ElectionType;
import com.cepsandik.electionservice.enums.ParticipantType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ElectionPreviewResponse {

    private Long electionId;
    private String title;
    private String description;
    private ElectionStatus status;
    private ElectionType type;
    private ParticipantType participantType;
    private Instant startTime;
    private Instant endTime;
    private Boolean resultsPublic;
    private Integer candidateCount;
    private Integer accessCodeCount;
    private List<CandidateResponse> candidates;

    /** Seçim yayınlamaya hazır mı */
    private Boolean readyToPublish;

    /** Eksikler / uyarılar */
    private List<String> warnings;
}
