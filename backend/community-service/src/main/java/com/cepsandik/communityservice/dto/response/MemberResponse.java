package com.cepsandik.communityservice.dto.response;

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
public class MemberResponse {
    private Long id;
    private Long communityId;
    private String userId;
    private MemberRole role;
    private MemberStatus status;
    private LocalDateTime joinedAt;
}
