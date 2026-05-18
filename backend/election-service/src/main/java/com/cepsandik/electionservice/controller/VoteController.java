package com.cepsandik.electionservice.controller;

import com.cepsandik.electionservice.dto.request.CastVoteRequest;
import com.cepsandik.electionservice.dto.request.SpoilBallotRequest;
import com.cepsandik.electionservice.dto.request.VerifyAccessCodeRequest;
import com.cepsandik.electionservice.dto.response.*;
import com.cepsandik.electionservice.service.SpoiledBallotService;
import com.cepsandik.electionservice.service.VoteService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/elections/{electionId}/votes")
@RequiredArgsConstructor
@Tag(name = "Voting", description = "E2E-V anonymous voting APIs")
public class VoteController {

    private final VoteService voteService;
    private final SpoiledBallotService spoiledBallotService;

    @Operation(summary = "Benaloh challenge: bir ballot'u spoil et (cast-as-intended doğrula)")
    @PostMapping("/spoil")
    public ResponseEntity<ApiResponse<SpoilBallotResponse>> spoilBallot(
            @PathVariable Long electionId,
            @Valid @RequestBody SpoilBallotRequest request) {

        SpoilBallotResponse response = spoiledBallotService.spoilBallot(electionId, request);
        return ResponseEntity.ok(ApiResponse.success(
                response.isVerified()
                        ? "Şifreleme doğrulandı (bu ballot sayılmayacak)"
                        : "Şifreleme doğrulanamadı",
                response));
    }

    @Operation(summary = "Verify an access code and return election data")
    @PostMapping("/verify-access")
    public ResponseEntity<ApiResponse<AccessVerificationResponse>> verifyAccessCode(
            @PathVariable Long electionId,
            @Valid @RequestBody VerifyAccessCodeRequest request) {

        AccessVerificationResponse response = voteService.verifyAccessCode(electionId, request.getCode());
        return ResponseEntity.ok(ApiResponse.success("Erisim kodu dogrulandi", response));
    }

    @Operation(summary = "Issue an eligibility credential for the current voter")
    @PostMapping("/token")
    public ResponseEntity<ApiResponse<VoteTokenResponse>> generateVoteToken(
            @PathVariable Long electionId,
            @RequestHeader("X-User-Id") String userId) {

        VoteTokenResponse response = voteService.generateVoteToken(electionId, userId);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Oy kullanma belgesi olusturuldu", response));
    }

    @Operation(summary = "Issue an anonymous voting credential")
    @PostMapping("/credentials/blind-issue")
    public ResponseEntity<ApiResponse<VoteTokenResponse>> issueBlindCredential(
            @PathVariable Long electionId,
            @RequestHeader("X-User-Id") String userId) {

        VoteTokenResponse response = voteService.generateVoteToken(electionId, userId);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Anonim oy kullanma belgesi olusturuldu", response));
    }

    @Operation(summary = "Cast an anonymous encrypted ballot")
    @PostMapping
    public ResponseEntity<ApiResponse<VoteResponse>> castVote(
            @PathVariable Long electionId,
            @Valid @RequestBody CastVoteRequest request) {

        VoteResponse response = voteService.castVote(electionId, request);
        if (response.getAlreadyVoted()) {
            return ResponseEntity.ok(ApiResponse.success("Bu anonim nullifier daha once kullanilmis", response));
        }
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Sifreli oyunuz kaydedildi", response));
    }

    @Operation(summary = "Return credential issuance status; does not reveal ballot status")
    @GetMapping("/my-status")
    public ResponseEntity<ApiResponse<VoteResponse>> getMyVoteStatus(
            @PathVariable Long electionId,
            @RequestHeader("X-User-Id") String userId) {

        VoteResponse response = voteService.getMyVoteStatus(electionId, userId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @Operation(summary = "Disabled in E2E-V mode; use tracking-code bulletin lookup")
    @GetMapping("/my-proof")
    public ResponseEntity<ApiResponse<VoteProofResponse>> getMyVoteProof(
            @PathVariable Long electionId,
            @RequestHeader("X-User-Id") String userId) {

        VoteProofResponse response = voteService.getMyVoteProof(electionId, userId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @Operation(summary = "Return public ballot proof by tracking code")
    @GetMapping("/bulletin/ballots/{trackingCode}")
    public ResponseEntity<ApiResponse<VoteProofResponse>> getVoteProofByTrackingCode(
            @PathVariable Long electionId,
            @PathVariable String trackingCode) {

        VoteProofResponse response = voteService.getVoteProofByTrackingCode(electionId, trackingCode);
        return ResponseEntity.ok(ApiResponse.success("Public bulletin oy kaniti getirildi", response));
    }

    @Operation(summary = "Return high-level election stats")
    @GetMapping("/stats")
    public ResponseEntity<ApiResponse<ElectionStatsResponse>> getElectionStats(
            @PathVariable Long electionId,
            @RequestHeader("X-User-Id") String userId) {

        ElectionStatsResponse response = voteService.getElectionStats(electionId, userId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
