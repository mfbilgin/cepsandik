package com.cepsandik.communityservice.mapper;

import com.cepsandik.communityservice.dto.request.CreateCommunityRequest;
import com.cepsandik.communityservice.dto.response.CommunityResponse;
import com.cepsandik.communityservice.entity.Community;
import com.cepsandik.communityservice.entity.CommunityMember;
import com.cepsandik.communityservice.enums.MemberRole;
import com.cepsandik.communityservice.enums.MemberStatus;
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
        community.setCoverImageUrl(request.getCoverImageUrl());
        return community;
    }

    public CommunityResponse toResponse(Community community, long memberCount, MemberRole userRole) {
        return toResponse(community, memberCount, userRole, null);
    }

    public CommunityResponse toResponse(Community community, long memberCount, MemberRole userRole, MemberStatus userStatus) {
        return CommunityResponse.builder()
                .id(community.getId())
                .name(community.getName())
                .description(community.getDescription())
                .visibility(community.getVisibility())
                .ownerId(community.getOwnerId())
                .memberCount(memberCount)
                .userRole(userRole)
                .userStatus(userStatus)
                .nameChangeCount(community.getNameChangeCount())
                .createdAt(community.getCreatedAt())
                .updatedAt(community.getUpdatedAt())
                .coverImageUrl(community.getCoverImageUrl())
                .build();
    }

    public CommunityResponse toResponse(Community community, long memberCount, CommunityMember member) {
        MemberRole role = member != null ? member.getRole() : null;
        MemberStatus status = member != null ? member.getStatus() : null;
        return toResponse(community, memberCount, role, status);
    }
}
