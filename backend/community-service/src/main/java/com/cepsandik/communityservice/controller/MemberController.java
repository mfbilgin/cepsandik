package com.cepsandik.communityservice.controller;

import com.cepsandik.communityservice.dto.request.UpdateMemberRoleRequest;
import com.cepsandik.communityservice.dto.response.ApiResponse;
import com.cepsandik.communityservice.dto.response.MemberResponse;
import com.cepsandik.communityservice.dto.response.PageResponse;
import com.cepsandik.communityservice.service.MemberService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/communities/{communityId}/members")
@RequiredArgsConstructor
public class MemberController {

    private final MemberService memberService;

    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<MemberResponse>>> getMembers(
            @PathVariable Long communityId,
            @RequestHeader("X-User-Id") String userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        PageResponse<MemberResponse> members = memberService.getMembers(communityId, userId, page, size);
        return ResponseEntity.ok(ApiResponse.success(members));
    }

    @GetMapping("/pending")
    public ResponseEntity<ApiResponse<PageResponse<MemberResponse>>> getPendingMembers(
            @PathVariable Long communityId,
            @RequestHeader("X-User-Id") String userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        PageResponse<MemberResponse> members = memberService.getPendingMembers(communityId, userId, page, size);
        return ResponseEntity.ok(ApiResponse.success(members));
    }

    @PutMapping("/{memberId}/role")
    public ResponseEntity<ApiResponse<MemberResponse>> updateMemberRole(
            @PathVariable Long communityId,
            @PathVariable Long memberId,
            @Valid @RequestBody UpdateMemberRoleRequest request,
            @RequestHeader("X-User-Id") String userId) {

        MemberResponse response = memberService.updateMemberRole(communityId, memberId, request, userId);
        return ResponseEntity.ok(ApiResponse.success("Üye rolü güncellendi", response));
    }

    @PutMapping("/{memberId}/approve")
    public ResponseEntity<ApiResponse<MemberResponse>> approveMember(
            @PathVariable Long communityId,
            @PathVariable Long memberId,
            @RequestHeader("X-User-Id") String userId) {

        MemberResponse response = memberService.approveMember(communityId, memberId, userId);
        return ResponseEntity.ok(ApiResponse.success("Üye onaylandı", response));
    }

    @PutMapping("/{memberId}/reject")
    public ResponseEntity<ApiResponse<Void>> rejectMember(
            @PathVariable Long communityId,
            @PathVariable Long memberId,
            @RequestHeader("X-User-Id") String userId) {

        memberService.rejectMember(communityId, memberId, userId);
        return ResponseEntity.ok(ApiResponse.success("Üye reddedildi", null));
    }

    @DeleteMapping("/{memberId}")
    public ResponseEntity<ApiResponse<Void>> removeMember(
            @PathVariable Long communityId,
            @PathVariable Long memberId,
            @RequestHeader("X-User-Id") String userId) {

        memberService.removeMember(communityId, memberId, userId);
        return ResponseEntity.ok(ApiResponse.success("Üye topluluktan çıkarıldı", null));
    }

    @DeleteMapping("/leave")
    public ResponseEntity<ApiResponse<Void>> leaveCommunity(
            @PathVariable Long communityId,
            @RequestHeader("X-User-Id") String userId) {

        memberService.leaveCommunity(communityId, userId);
        return ResponseEntity.ok(ApiResponse.success("Topluluktan ayrıldınız", null));
    }
}
