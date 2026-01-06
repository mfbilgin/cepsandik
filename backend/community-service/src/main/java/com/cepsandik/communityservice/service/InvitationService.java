package com.cepsandik.communityservice.service;

import com.cepsandik.communityservice.dto.request.CreateInvitationRequest;
import com.cepsandik.communityservice.dto.request.JoinCommunityRequest;
import com.cepsandik.communityservice.dto.response.CommunityResponse;
import com.cepsandik.communityservice.dto.response.InvitationResponse;
import com.cepsandik.communityservice.entity.Community;
import com.cepsandik.communityservice.entity.CommunityInvitation;
import com.cepsandik.communityservice.entity.CommunityMember;
import com.cepsandik.communityservice.enums.CommunityVisibility;
import com.cepsandik.communityservice.enums.MemberRole;
import com.cepsandik.communityservice.enums.MemberStatus;
import com.cepsandik.communityservice.exception.ApiException;
import com.cepsandik.communityservice.mapper.InvitationMapper;
import com.cepsandik.communityservice.repository.CommunityInvitationRepository;
import com.cepsandik.communityservice.repository.CommunityMemberRepository;
import com.cepsandik.communityservice.repository.CommunityRepository;
import com.cepsandik.communityservice.util.CodeGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class InvitationService {

    private final CommunityInvitationRepository invitationRepository;
    private final CommunityRepository communityRepository;
    private final CommunityMemberRepository memberRepository;
    private final CodeGenerator codeGenerator;
    private final CommunityService communityService;
    private final InvitationMapper invitationMapper;

    @Transactional
    public InvitationResponse createInvitation(Long communityId, CreateInvitationRequest request, String userId) {
        communityRepository.findByIdAndIsDeletedFalse(communityId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Topluluk bulunamadı"));

        // Yetki kontrolü
        CommunityMember member = memberRepository.findByCommunityIdAndUserId(communityId, userId)
                .orElseThrow(() -> new ApiException(HttpStatus.FORBIDDEN, "Bu topluluğun üyesi değilsiniz"));

        if (member.getRole() != MemberRole.OWNER && member.getRole() != MemberRole.ADMIN) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Davet oluşturma yetkiniz yok");
        }

        // Benzersiz kod oluştur
        String code;
        do {
            code = codeGenerator.generateInvitationCode();
        } while (invitationRepository.findByCode(code).isPresent());

        // Davet oluştur
        CommunityInvitation invitation = new CommunityInvitation();
        invitation.setCommunityId(communityId);
        invitation.setCode(code);
        invitation.setMaxUses(request.getMaxUses());
        invitation.setCurrentUses(0);
        invitation.setCreatedBy(userId);
        invitation.setIsActive(true);

        if (request.getExpiresInHours() != null) {
            invitation.setExpiresAt(LocalDateTime.now().plusHours(request.getExpiresInHours()));
        }

        CommunityInvitation saved = invitationRepository.save(invitation);

        log.info("Davet oluşturuldu: communityId={}, code={}", communityId, code);

        return invitationMapper.toResponse(saved);
    }

    @Transactional
    public CommunityResponse joinCommunity(JoinCommunityRequest request, String userId) {
        CommunityInvitation invitation = invitationRepository.findByCode(request.getCode())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Geçersiz davet kodu"));

        // Davet doğrulama
        if (!invitation.getIsActive()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Bu davet artık aktif değil");
        }

        if (invitation.getExpiresAt() != null && invitation.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Bu davetin süresi dolmuş");
        }

        if (invitation.getMaxUses() != null && invitation.getCurrentUses() >= invitation.getMaxUses()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Bu davet maksimum kullanım sayısına ulaşmış");
        }

        Long communityId = invitation.getCommunityId();
        Community community = communityRepository.findByIdAndIsDeletedFalse(communityId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Topluluk bulunamadı"));

        // Zaten üye mi kontrol et
        if (memberRepository.existsByCommunityIdAndUserId(communityId, userId)) {
            throw new ApiException(HttpStatus.CONFLICT, "Bu topluluğun zaten üyesisiniz");
        }

        // Üye ekle
        CommunityMember newMember = new CommunityMember();
        newMember.setCommunityId(communityId);
        newMember.setUserId(userId);
        newMember.setRole(MemberRole.MEMBER);

        // Public topluluklar için otomatik onay, private için beklemede
        if (community.getVisibility() == CommunityVisibility.PUBLIC) {
            newMember.setStatus(MemberStatus.APPROVED);
        } else {
            newMember.setStatus(MemberStatus.PENDING);
        }

        memberRepository.save(newMember);

        // Davet kullanım sayısını artır
        invitation.setCurrentUses(invitation.getCurrentUses() + 1);
        invitationRepository.save(invitation);

        log.info("Kullanıcı topluluğa katıldı: communityId={}, userId={}, status={}",
                communityId, userId, newMember.getStatus());

        return communityService.getCommunityById(communityId, userId);
    }

    public List<InvitationResponse> getCommunityInvitations(Long communityId, String userId) {
        // Yetki kontrolü
        CommunityMember member = memberRepository.findByCommunityIdAndUserId(communityId, userId)
                .orElseThrow(() -> new ApiException(HttpStatus.FORBIDDEN, "Bu topluluğun üyesi değilsiniz"));

        if (member.getRole() != MemberRole.OWNER && member.getRole() != MemberRole.ADMIN) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Davetleri görme yetkiniz yok");
        }

        return invitationMapper.toResponseList(
                invitationRepository.findByCommunityIdAndIsActiveTrue(communityId));
    }

    @Transactional
    public void deactivateInvitation(Long communityId, Long invitationId, String userId) {
        // Yetki kontrolü
        CommunityMember member = memberRepository.findByCommunityIdAndUserId(communityId, userId)
                .orElseThrow(() -> new ApiException(HttpStatus.FORBIDDEN, "Bu topluluğun üyesi değilsiniz"));

        if (member.getRole() != MemberRole.OWNER && member.getRole() != MemberRole.ADMIN) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Davet iptal etme yetkiniz yok");
        }

        CommunityInvitation invitation = invitationRepository.findById(invitationId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Davet bulunamadı"));

        if (!invitation.getCommunityId().equals(communityId)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Bu davet bu topluluğa ait değil");
        }

        invitation.setIsActive(false);
        invitationRepository.save(invitation);

        log.info("Davet iptal edildi: communityId={}, invitationId={}", communityId, invitationId);
    }
}
