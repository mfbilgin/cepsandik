package com.cepsandik.communityservice.service;

import com.cepsandik.communityservice.dto.response.StatisticsResponse;
import com.cepsandik.communityservice.entity.Community;
import com.cepsandik.communityservice.entity.CommunityMember;
import com.cepsandik.communityservice.enums.MemberRole;
import com.cepsandik.communityservice.enums.MemberStatus;
import com.cepsandik.communityservice.exception.ApiException;
import com.cepsandik.communityservice.repository.CommunityInvitationRepository;
import com.cepsandik.communityservice.repository.CommunityMemberRepository;
import com.cepsandik.communityservice.repository.CommunityRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Slf4j
public class StatisticsService {

    private final CommunityRepository communityRepository;
    private final CommunityMemberRepository memberRepository;
    private final CommunityInvitationRepository invitationRepository;

    public StatisticsResponse getCommunityStatistics(Long communityId, String userId) {
        Community community = communityRepository.findByIdAndIsDeletedFalse(communityId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Topluluk bulunamadı"));

        // Üyelik kontrolü
        CommunityMember member = memberRepository.findByCommunityIdAndUserId(communityId, userId)
                .orElseThrow(() -> new ApiException(HttpStatus.FORBIDDEN, "Bu topluluğun üyesi değilsiniz"));

        // Sadece admin ve owner istatistikleri görebilir
        if (member.getRole() != MemberRole.OWNER && member.getRole() != MemberRole.ADMIN) {
            throw new ApiException(HttpStatus.FORBIDDEN, "İstatistikleri görme yetkiniz yok");
        }

        long totalMembers = memberRepository.countByCommunityIdAndStatus(communityId, MemberStatus.APPROVED);
        long pendingMembers = memberRepository.countByCommunityIdAndStatus(communityId, MemberStatus.PENDING);
        long adminCount = memberRepository.countByCommunityIdAndRole(communityId, MemberRole.ADMIN);
        long activeInvitations = invitationRepository.countByCommunityIdAndIsActiveTrue(communityId);
        long totalInvitationsUsed = invitationRepository.sumCurrentUsesByCommunityId(communityId);

        // Son aktivite - en son katılan üyenin tarihi
        LocalDateTime lastActivity = memberRepository.findLastJoinedAtByCommunityId(communityId)
                .orElse(community.getCreatedAt());

        return StatisticsResponse.builder()
                .communityId(community.getId())
                .communityName(community.getName())
                .totalMembers(totalMembers)
                .pendingMembers(pendingMembers)
                .adminCount(adminCount)
                .activeInvitations(activeInvitations)
                .totalInvitationsUsed(totalInvitationsUsed)
                .createdAt(community.getCreatedAt())
                .lastActivityAt(lastActivity)
                .build();
    }
}
