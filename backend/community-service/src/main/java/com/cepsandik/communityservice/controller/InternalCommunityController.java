package com.cepsandik.communityservice.controller;

import com.cepsandik.communityservice.entity.CommunityMember;
import com.cepsandik.communityservice.enums.MemberStatus;
import com.cepsandik.communityservice.repository.CommunityMemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Servisler arası iletişim için dahili endpoint'ler.
 * Bu endpoint'ler Docker ağı üzerinden diğer servisler tarafından çağrılır.
 * Gateway üzerinden dışarıya açık DEĞİLDİR.
 */
@RestController
@RequestMapping("/internal/api/v1/communities")
@RequiredArgsConstructor
public class InternalCommunityController {

    private final CommunityMemberRepository memberRepository;

    /**
     * Topluluk üyelerinin userId listesini döndürür.
     * Sadece APPROVED statüsündeki üyeler döner.
     */
    @GetMapping("/{communityId}/member-user-ids")
    public ResponseEntity<List<String>> getMemberUserIds(@PathVariable Long communityId) {
        List<String> userIds = memberRepository
                .findByCommunityIdAndStatus(communityId, MemberStatus.APPROVED)
                .stream()
                .map(CommunityMember::getUserId)
                .toList();

        return ResponseEntity.ok(userIds);
    }
}
