package com.cepsandik.communityservice.mapper;

import com.cepsandik.communityservice.dto.request.CreateCommunityRequest;
import com.cepsandik.communityservice.dto.response.CommunityResponse;
import com.cepsandik.communityservice.entity.Community;
import com.cepsandik.communityservice.entity.CommunityMember;
import com.cepsandik.communityservice.enums.MemberRole;
import org.springframework.stereotype.Component;

@Component
public class CommunityMapper {

    public Community toEntity(CreateCommunityRequest request, String ownerId) {
        Community community = new Community();
        community.setName(request.getName());
        community.setDescription(request.getDescription());
        community.setVisibility(request.getVisibility());
        community.setOwnerId(ownerId);
        community.setIsDeleted(false);
        return community;
    }

    public CommunityResponse toResponse(Community community, long memberCount, MemberRole userRole) {
        return CommunityResponse.builder()
                .id(community.getId())
                .name(community.getName())
                .description(community.getDescription())
                .visibility(community.getVisibility())
                .ownerId(community.getOwnerId())
                .memberCount(memberCount)
                .userRole(userRole)
                .createdAt(community.getCreatedAt())
                .updatedAt(community.getUpdatedAt())
                .build();
    }

    public CommunityResponse toResponse(Community community, long memberCount, CommunityMember member) {
        MemberRole role = member != null ? member.getRole() : null;
        return toResponse(community, memberCount, role);
    }
}
