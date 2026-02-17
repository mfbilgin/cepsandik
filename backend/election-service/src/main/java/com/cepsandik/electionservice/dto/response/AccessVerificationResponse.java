package com.cepsandik.electionservice.dto.response;

import com.cepsandik.electionservice.enums.ElectionStatus;
import com.cepsandik.electionservice.enums.ElectionType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AccessVerificationResponse {

    private Long electionId;
    private String electionTitle;
    private String electionDescription;
    private ElectionStatus status;
    private ElectionType type;
    private Integer maxSelections;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private Integer candidateCount;
    private List<CandidateResponse> candidates;
    private Boolean anonymousVoting;
}
