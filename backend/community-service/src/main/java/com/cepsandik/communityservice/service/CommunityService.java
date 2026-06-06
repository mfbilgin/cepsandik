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
import org.slf4j.MDC;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

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
    private final FileUploadService fileUploadService;

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

        MDC.put("event_type", "community_created");
        log.info("Topluluk oluşturuldu: id={}, name={}, owner={}", saved.getId(), saved.getName(), userId);
        MDC.remove("event_type");

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

        // Topluluk detayı herkese açıktır; PRIVATE'in tek farkı katılımın
        // onaya tabi olmasıdır (bkz MemberService.joinCommunity).
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
        if (request.getName() != null && !request.getName().equals(community.getName())) {
            community.setNameChangeCount(community.getNameChangeCount() + 1);
            community.setName(request.getName());
        }
        if (request.getDescription() != null) {
            community.setDescription(request.getDescription());
        }
        if (request.getVisibility() != null) {
            community.setVisibility(request.getVisibility());
        }
        if (request.getCoverImageUrl() != null) {
            community.setCoverImageUrl(request.getCoverImageUrl());
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

    /**
     * Keşfet — kullanıcının henüz üye olmadığı (PUBLIC + PRIVATE) tüm topluluklar.
     * PRIVATE'in tek farkı katılımın onaya tabi olması; listede gizli kalmaz.
     */
    public PageResponse<CommunityResponse> discoverCommunities(String userId, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());

        // Kullanıcının halihazırda üye olduğu (PENDING dahil) topluluk id'leri hariç tut
        List<Long> excludeIds = memberRepository.findByUserIdAndStatus(userId, MemberStatus.APPROVED)
                .stream().map(CommunityMember::getCommunityId).collect(Collectors.toList());
        List<CommunityMember> pendings = memberRepository.findByUserIdAndStatus(userId, MemberStatus.PENDING);
        for (CommunityMember p : pendings) excludeIds.add(p.getCommunityId());

        // JPQL'in boş IN listesinde DB-specific davranmaması için sentinel
        if (excludeIds.isEmpty()) excludeIds = List.of(-1L);

        Page<Community> communities = communityRepository.discoverExcluding(excludeIds, pageable);

        List<CommunityResponse> responses = communities.stream()
                .map(c -> buildCommunityResponse(c, userId))
                .collect(Collectors.toList());

        return PageResponse.of(responses, page, size, communities.getTotalElements());
    }

    /**
     * Topluluk logosunu S3'e yükler, eski logoyu temizler ve yeni URL'i kaydeder.
     * Yalnızca OWNER/ADMIN yapabilir.
     */
    @Transactional
    public CommunityResponse uploadLogo(Long communityId, String userId, MultipartFile file) {
        Community community = communityRepository.findByIdAndIsDeletedFalse(communityId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Topluluk bulunamadı"));

        CommunityMember member = memberRepository.findByCommunityIdAndUserId(communityId, userId)
                .orElseThrow(() -> new ApiException(HttpStatus.FORBIDDEN, "Bu topluluğun üyesi değilsiniz"));

        if (member.getRole() != MemberRole.OWNER && member.getRole() != MemberRole.ADMIN) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Logo güncelleme yetkiniz yok");
        }

        String oldUrl = community.getCoverImageUrl();
        String newUrl = fileUploadService.uploadCommunityLogo(communityId, file);

        community.setCoverImageUrl(newUrl);
        Community saved = communityRepository.save(community);

        // En son: eski S3 nesnesini temizle (best-effort; S3 dışı URL'ler dokunulmaz)
        if (oldUrl != null && !oldUrl.equals(newUrl)) {
            fileUploadService.deleteLogo(oldUrl);
        }

        log.info("Topluluk logosu güncellendi: communityId={}, userId={}", communityId, userId);
        return buildCommunityResponse(saved, userId);
    }

    private CommunityResponse buildCommunityResponse(Community community, String userId) {
        long memberCount = memberRepository.countByCommunityIdAndStatus(community.getId(), MemberStatus.APPROVED);

        CommunityMember member = memberRepository.findByCommunityIdAndUserId(community.getId(), userId)
                .orElse(null);

        return communityMapper.toResponse(community, memberCount, member);
    }

    @org.springframework.beans.factory.annotation.Value("${UNSPLASH_ACCESS_KEY:}")
    private String unsplashAccessKey;

    public Object searchUnsplashImages(String query) {
        org.springframework.web.client.RestTemplate restTemplate = new org.springframework.web.client.RestTemplate();
        String url = "https://api.unsplash.com/search/photos?query=" + query + "&per_page=15&client_id=" + unsplashAccessKey;
        try {
            return restTemplate.getForObject(url, Object.class);
        } catch (Exception e) {
            log.error("Error fetching Unsplash images: ", e);
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "Resimler yüklenemedi");
        }
    }
}
