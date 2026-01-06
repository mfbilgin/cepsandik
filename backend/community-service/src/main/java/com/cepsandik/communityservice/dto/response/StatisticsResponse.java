package com.cepsandik.communityservice.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StatisticsResponse {
    private Long communityId;
    private String communityName;
    private long totalMembers;
    private long pendingMembers;
    private long adminCount;
    private long activeInvitations;
    private long totalInvitationsUsed;
    private LocalDateTime createdAt;
    private LocalDateTime lastActivityAt;
}
