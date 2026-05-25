package com.cepsandik.communityservice.dto.response;

import com.cepsandik.communityservice.enums.CommunityVisibility;
import com.cepsandik.communityservice.enums.MemberRole;
import com.cepsandik.communityservice.enums.MemberStatus;
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
    private MemberStatus userStatus; // Current user's membership status (PENDING/APPROVED), null if not a member
    private Integer nameChangeCount;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private String coverImageUrl;
}
