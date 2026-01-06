package com.cepsandik.communityservice.service;

import com.cepsandik.communityservice.dto.request.UpdateMemberRoleRequest;
import com.cepsandik.communityservice.dto.response.MemberResponse;
import com.cepsandik.communityservice.dto.response.PageResponse;
import com.cepsandik.communityservice.entity.CommunityMember;
import com.cepsandik.communityservice.enums.MemberRole;
import com.cepsandik.communityservice.enums.MemberStatus;
import com.cepsandik.communityservice.exception.ApiException;
import com.cepsandik.communityservice.mapper.MemberMapper;
import com.cepsandik.communityservice.repository.CommunityMemberRepository;
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

@Service
@RequiredArgsConstructor
@Slf4j
public class MemberService {

    private final CommunityMemberRepository memberRepository;
    private final MemberMapper memberMapper;

    public PageResponse<MemberResponse> getMembers(Long communityId, String userId, int page, int size) {
        validateMembership(communityId, userId);

        Pageable pageable = PageRequest.of(page, size, Sort.by("joinedAt").descending());
        Page<CommunityMember> memberPage = memberRepository.findByCommunityIdAndStatus(
                communityId, MemberStatus.APPROVED, pageable);

        List<MemberResponse> members = memberMapper.toResponseList(memberPage.getContent());
        return PageResponse.of(members, page, size, memberPage.getTotalElements());
    }

    public PageResponse<MemberResponse> getPendingMembers(Long communityId, String userId, int page, int size) {
        validateAdminOrOwner(communityId, userId);

        Pageable pageable = PageRequest.of(page, size, Sort.by("joinedAt").ascending());
        Page<CommunityMember> memberPage = memberRepository.findByCommunityIdAndStatus(
                communityId, MemberStatus.PENDING, pageable);

        List<MemberResponse> members = memberMapper.toResponseList(memberPage.getContent());
        return PageResponse.of(members, page, size, memberPage.getTotalElements());
    }

    @Transactional
    public MemberResponse updateMemberRole(Long communityId, Long memberId,
            UpdateMemberRoleRequest request, String userId) {
        validateOwner(communityId, userId);

        CommunityMember targetMember = memberRepository.findById(memberId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Üye bulunamadı"));

        if (!targetMember.getCommunityId().equals(communityId)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Üye bu topluluğa ait değil");
        }

        // Owner rolü değiştirilemez
        if (targetMember.getRole() == MemberRole.OWNER) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Topluluk sahibinin rolü değiştirilemez");
        }

        // OWNER rolü atanamaz
        if (request.getRole() == MemberRole.OWNER) {
            throw new ApiException(HttpStatus.FORBIDDEN, "OWNER rolü atanamaz");
        }

        targetMember.setRole(request.getRole());
        CommunityMember updated = memberRepository.save(targetMember);

        log.info("Üye rolü güncellendi: communityId={}, memberId={}, newRole={}",
                communityId, memberId, request.getRole());

        return memberMapper.toResponse(updated);
    }

    @Transactional
    public MemberResponse approveMember(Long communityId, Long memberId, String userId) {
        validateAdminOrOwner(communityId, userId);

        CommunityMember member = memberRepository.findById(memberId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Üye bulunamadı"));

        if (!member.getCommunityId().equals(communityId)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Üye bu topluluğa ait değil");
        }

        if (member.getStatus() != MemberStatus.PENDING) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Üye zaten onaylanmış veya reddedilmiş");
        }

        member.setStatus(MemberStatus.APPROVED);
        CommunityMember updated = memberRepository.save(member);

        log.info("Üye onaylandı: communityId={}, memberId={}", communityId, memberId);

        return memberMapper.toResponse(updated);
    }

    @Transactional
    public void rejectMember(Long communityId, Long memberId, String userId) {
        validateAdminOrOwner(communityId, userId);

        CommunityMember member = memberRepository.findById(memberId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Üye bulunamadı"));

        if (!member.getCommunityId().equals(communityId)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Üye bu topluluğa ait değil");
        }

        if (member.getStatus() != MemberStatus.PENDING) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Üye zaten onaylanmış veya reddedilmiş");
        }

        member.setStatus(MemberStatus.REJECTED);
        memberRepository.save(member);

        log.info("Üye reddedildi: communityId={}, memberId={}", communityId, memberId);
    }

    @Transactional
    public void removeMember(Long communityId, Long memberId, String userId) {
        CommunityMember requester = validateAdminOrOwner(communityId, userId);

        CommunityMember targetMember = memberRepository.findById(memberId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Üye bulunamadı"));

        if (!targetMember.getCommunityId().equals(communityId)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Üye bu topluluğa ait değil");
        }

        // Owner'ı çıkaramaz
        if (targetMember.getRole() == MemberRole.OWNER) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Topluluk sahibi çıkarılamaz");
        }

        // Admin sadece normal üyeleri çıkarabilir
        if (requester.getRole() == MemberRole.ADMIN && targetMember.getRole() == MemberRole.ADMIN) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Diğer yöneticileri çıkarma yetkiniz yok");
        }

        memberRepository.delete(targetMember);

        log.info("Üye çıkarıldı: communityId={}, memberId={}", communityId, memberId);
    }

    @Transactional
    public void leaveCommunity(Long communityId, String userId) {
        CommunityMember member = memberRepository.findByCommunityIdAndUserId(communityId, userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Bu topluluğun üyesi değilsiniz"));

        // Owner topluluktan ayrılamaz
        if (member.getRole() == MemberRole.OWNER) {
            throw new ApiException(HttpStatus.FORBIDDEN,
                    "Topluluk sahibi olarak ayrılamazsınız. Önce topluluğu silmeniz veya sahipliği devretmeniz gerekir");
        }

        memberRepository.delete(member);

        log.info("Kullanıcı topluluktan ayrıldı: communityId={}, userId={}", communityId, userId);
    }

    // === Helper Methods ===

    private void validateMembership(Long communityId, String userId) {
        if (!memberRepository.existsByCommunityIdAndUserId(communityId, userId)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Bu topluluğun üyesi değilsiniz");
        }
    }

    private CommunityMember validateAdminOrOwner(Long communityId, String userId) {
        CommunityMember member = memberRepository.findByCommunityIdAndUserId(communityId, userId)
                .orElseThrow(() -> new ApiException(HttpStatus.FORBIDDEN, "Bu topluluğun üyesi değilsiniz"));

        if (member.getRole() != MemberRole.OWNER && member.getRole() != MemberRole.ADMIN) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Bu işlem için yetkiniz yok");
        }

        return member;
    }

    private CommunityMember validateOwner(Long communityId, String userId) {
        CommunityMember member = memberRepository.findByCommunityIdAndUserId(communityId, userId)
                .orElseThrow(() -> new ApiException(HttpStatus.FORBIDDEN, "Bu topluluğun üyesi değilsiniz"));

        if (member.getRole() != MemberRole.OWNER) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Bu işlem sadece topluluk sahibi tarafından yapılabilir");
        }

        return member;
    }
}
