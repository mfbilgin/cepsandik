package com.cepsandik.electionservice.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VoteTokenResponse {

    private String token;
    private Long electionId;
    private String electionTitle;
    private Boolean isUsed;
    private LocalDateTime createdAt;
    private Instant usedAt;
}
