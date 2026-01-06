package com.cepsandik.communityservice.controller;

import com.cepsandik.communityservice.dto.request.CreateInvitationRequest;
import com.cepsandik.communityservice.dto.request.JoinCommunityRequest;
import com.cepsandik.communityservice.dto.response.ApiResponse;
import com.cepsandik.communityservice.dto.response.CommunityResponse;
import com.cepsandik.communityservice.dto.response.InvitationResponse;
import com.cepsandik.communityservice.service.InvitationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class InvitationController {

    private final InvitationService invitationService;

    @PostMapping("/communities/{communityId}/invitations")
    public ResponseEntity<ApiResponse<InvitationResponse>> createInvitation(
            @PathVariable Long communityId,
            @Valid @RequestBody CreateInvitationRequest request,
            @RequestHeader("X-User-Id") String userId) {

        InvitationResponse response = invitationService.createInvitation(communityId, request, userId);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Davet başarıyla oluşturuldu", response));
    }

    @PostMapping("/communities/join")
    public ResponseEntity<ApiResponse<CommunityResponse>> joinCommunity(
            @Valid @RequestBody JoinCommunityRequest request,
            @RequestHeader("X-User-Id") String userId) {

        CommunityResponse response = invitationService.joinCommunity(request, userId);
        return ResponseEntity.ok(ApiResponse.success("Topluluğa başarıyla katıldınız", response));
    }

    @GetMapping("/communities/{communityId}/invitations")
    public ResponseEntity<ApiResponse<List<InvitationResponse>>> getCommunityInvitations(
            @PathVariable Long communityId,
            @RequestHeader("X-User-Id") String userId) {

        List<InvitationResponse> invitations = invitationService.getCommunityInvitations(communityId, userId);
        return ResponseEntity.ok(ApiResponse.success(invitations));
    }

    @DeleteMapping("/communities/{communityId}/invitations/{invitationId}")
    public ResponseEntity<ApiResponse<Void>> deactivateInvitation(
            @PathVariable Long communityId,
            @PathVariable Long invitationId,
            @RequestHeader("X-User-Id") String userId) {

        invitationService.deactivateInvitation(communityId, invitationId, userId);
        return ResponseEntity.ok(ApiResponse.success("Davet başarıyla iptal edildi", null));
    }
}
