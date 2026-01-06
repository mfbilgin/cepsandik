package com.cepsandik.communityservice.mapper;

import com.cepsandik.communityservice.dto.response.InvitationResponse;
import com.cepsandik.communityservice.entity.CommunityInvitation;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

@Component
public class InvitationMapper {

    public InvitationResponse toResponse(CommunityInvitation invitation) {
        return InvitationResponse.builder()
                .id(invitation.getId())
                .code(invitation.getCode())
                .maxUses(invitation.getMaxUses())
                .currentUses(invitation.getCurrentUses())
                .expiresAt(invitation.getExpiresAt())
                .createdBy(invitation.getCreatedBy())
                .createdAt(invitation.getCreatedAt())
                .isActive(invitation.getIsActive())
                .build();
    }

    public List<InvitationResponse> toResponseList(List<CommunityInvitation> invitations) {
        return invitations.stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }
}
