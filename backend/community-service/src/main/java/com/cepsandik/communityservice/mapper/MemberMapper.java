package com.cepsandik.communityservice.mapper;

import com.cepsandik.communityservice.dto.response.MemberResponse;
import com.cepsandik.communityservice.entity.CommunityMember;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

@Component
public class MemberMapper {

    public MemberResponse toResponse(CommunityMember member) {
        return MemberResponse.builder()
                .id(member.getId())
                .communityId(member.getCommunityId())
                .userId(member.getUserId())
                .role(member.getRole())
                .status(member.getStatus())
                .joinedAt(member.getJoinedAt())
                .build();
    }

    public List<MemberResponse> toResponseList(List<CommunityMember> members) {
        return members.stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }
}
