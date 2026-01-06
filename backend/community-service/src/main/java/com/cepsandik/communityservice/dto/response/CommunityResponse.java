package com.cepsandik.communityservice.dto.response;

import com.cepsandik.communityservice.enums.CommunityVisibility;
import com.cepsandik.communityservice.enums.MemberRole;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CommunityResponse {
    private Long id;
    private String name;
    private String description;
    private CommunityVisibility visibility;
    private String ownerId;
    private Long memberCount;
    private MemberRole userRole; // Current user's role
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
