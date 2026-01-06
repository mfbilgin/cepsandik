package com.cepsandik.communityservice.service;

import com.cepsandik.communityservice.dto.request.CreateCommunityRequest;
import com.cepsandik.communityservice.dto.request.UpdateCommunityRequest;
import com.cepsandik.communityservice.dto.response.CommunityResponse;
import com.cepsandik.communityservice.dto.response.PageResponse;
import com.cepsandik.communityservice.entity.Community;
import com.cepsandik.communityservice.entity.CommunityMember;
import com.cepsandik.communityservice.enums.MemberRole;
import com.cepsandik.communityservice.enums.MemberStatus;
import com.cepsandik.communityservice.exception.ApiException;
import com.cepsandik.communityservice.mapper.CommunityMapper;
import com.cepsandik.communityservice.repository.CommunityMemberRepository;
import com.cepsandik.communityservice.repository.CommunityRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class CommunityService {

    private final CommunityRepository communityRepository;
    private final CommunityMemberRepository memberRepository;
    private final CommunityMapper communityMapper;

    @Transactional
    public CommunityResponse createCommunity(CreateCommunityRequest request, String userId) {
        // Aynı isimde topluluk var mı kontrol et
        if (communityRepository.existsByNameAndOwnerIdAndIsDeletedFalse(request.getName(), userId)) {
            throw new ApiException(HttpStatus.CONFLICT, "Bu isimde bir topluluğunuz zaten var");
        }

        // Topluluk oluştur
        Community community = communityMapper.toEntity(request, userId);
        Community saved = communityRepository.save(community);

        // Sahibi üye olarak ekle
        CommunityMember ownerMember = new CommunityMember();
        ownerMember.setCommunityId(saved.getId());
        ownerMember.setUserId(userId);
        ownerMember.setRole(MemberRole.OWNER);
        ownerMember.setStatus(MemberStatus.APPROVED);
        memberRepository.save(ownerMember);

        log.info("Topluluk oluşturuldu: id={}, name={}, owner={}", saved.getId(), saved.getName(), userId);

        return buildCommunityResponse(saved, userId);
    }

    public List<CommunityResponse> getMyCommunities(String userId) {
        List<CommunityMember> memberships = memberRepository.findByUserIdAndStatus(userId, MemberStatus.APPROVED);

        return memberships.stream()
                .map(membership -> {
                    Community community = communityRepository.findByIdAndIsDeletedFalse(membership.getCommunityId())
                            .orElse(null);
                    if (community == null)
                        return null;
                    return buildCommunityResponse(community, userId);
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    public PageResponse<CommunityResponse> getMyCommunities(String userId, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("joinedAt").descending());
        Page<CommunityMember> memberships = memberRepository.findByUserIdAndStatus(userId, MemberStatus.APPROVED,
                pageable);

        List<CommunityResponse> communities = memberships.stream()
                .map(membership -> {
                    Community community = communityRepository.findByIdAndIsDeletedFalse(membership.getCommunityId())
                            .orElse(null);
                    if (community == null)
                        return null;
                    return buildCommunityResponse(community, userId);
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        return PageResponse.of(communities, page, size, memberships.getTotalElements());
    }

    public CommunityResponse getCommunityById(Long communityId, String userId) {
        Community community = communityRepository.findByIdAndIsDeletedFalse(communityId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Topluluk bulunamadı"));

        // Üyelik kontrolü
        if (!memberRepository.existsByCommunityIdAndUserId(communityId, userId)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Bu topluluğun üyesi değilsiniz");
        }

        return buildCommunityResponse(community, userId);
    }

    @Transactional
    public CommunityResponse updateCommunity(Long communityId, UpdateCommunityRequest request, String userId) {
        Community community = communityRepository.findByIdAndIsDeletedFalse(communityId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Topluluk bulunamadı"));

        // Yetki kontrolü
        CommunityMember member = memberRepository.findByCommunityIdAndUserId(communityId, userId)
                .orElseThrow(() -> new ApiException(HttpStatus.FORBIDDEN, "Bu topluluğun üyesi değilsiniz"));

        if (member.getRole() != MemberRole.OWNER && member.getRole() != MemberRole.ADMIN) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Topluluk güncelleme yetkiniz yok");
        }

        // Sadece dolu alanları güncelle
        if (request.getName() != null) {
            community.setName(request.getName());
        }
        if (request.getDescription() != null) {
            community.setDescription(request.getDescription());
        }
        if (request.getVisibility() != null) {
            community.setVisibility(request.getVisibility());
        }

        Community updated = communityRepository.save(community);

        log.info("Topluluk güncellendi: id={}, name={}", communityId, updated.getName());

        return buildCommunityResponse(updated, userId);
    }

    @Transactional
    public void deleteCommunity(Long communityId, String userId) {
        Community community = communityRepository.findByIdAndIsDeletedFalse(communityId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Topluluk bulunamadı"));

        // Sadece sahip silebilir
        if (!community.getOwnerId().equals(userId)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Bu topluluğu silme yetkiniz yok");
        }

        community.setIsDeleted(true);
        communityRepository.save(community);

        log.info("Topluluk silindi: id={}, name={}", communityId, community.getName());
    }

    public PageResponse<CommunityResponse> searchCommunities(String query, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        Page<Community> communities = communityRepository.searchCommunities(query, pageable);

        List<CommunityResponse> responses = communities.stream()
                .map(c -> communityMapper.toResponse(c,
                        memberRepository.countByCommunityIdAndStatus(c.getId(), MemberStatus.APPROVED),
                        (MemberRole) null))
                .collect(Collectors.toList());

        return PageResponse.of(responses, page, size, communities.getTotalElements());
    }

    private CommunityResponse buildCommunityResponse(Community community, String userId) {
        long memberCount = memberRepository.countByCommunityIdAndStatus(community.getId(), MemberStatus.APPROVED);

        CommunityMember member = memberRepository.findByCommunityIdAndUserId(community.getId(), userId)
                .orElse(null);

        return communityMapper.toResponse(community, memberCount, member);
    }
}
