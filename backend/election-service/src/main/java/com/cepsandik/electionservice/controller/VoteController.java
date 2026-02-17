package com.cepsandik.electionservice.controller;

import com.cepsandik.electionservice.dto.request.CastVoteRequest;
import com.cepsandik.electionservice.dto.request.VerifyAccessCodeRequest;
import com.cepsandik.electionservice.dto.response.*;
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
@Tag(name = "Voting", description = "Oy verme işlemleri API'leri")
public class VoteController {

    private final VoteService voteService;

    // ==================== Erişim Kodu Doğrulama ====================

    @Operation(summary = "Erişim kodunu doğrular ve seçim bilgilerini döndürür")
    @PostMapping("/verify-access")
    public ResponseEntity<ApiResponse<AccessVerificationResponse>> verifyAccessCode(
            @PathVariable Long electionId,
            @Valid @RequestBody VerifyAccessCodeRequest request) {

        AccessVerificationResponse response = voteService.verifyAccessCode(electionId, request.getCode());
        return ResponseEntity.ok(ApiResponse.success("Erişim kodu doğrulandı", response));
    }

    // ==================== Vote Token ====================

    @Operation(summary = "Oy kullanmak için tek kullanımlık token alır")
    @PostMapping("/token")
    public ResponseEntity<ApiResponse<VoteTokenResponse>> generateVoteToken(
            @PathVariable Long electionId,
            @RequestHeader("X-User-Id") String userId) {

        VoteTokenResponse response = voteService.generateVoteToken(electionId, userId);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Oy token'ı oluşturuldu", response));
    }

    // ==================== Oy Verme ====================

    @Operation(summary = "Oy kullanır (idempotent - aynı token ile tekrar istek yapılabilir)")
    @PostMapping
    public ResponseEntity<ApiResponse<VoteResponse>> castVote(
            @PathVariable Long electionId,
            @Valid @RequestBody CastVoteRequest request) {

        VoteResponse response = voteService.castVote(electionId, request);

        if (response.getAlreadyVoted()) {
            // İdempotent: daha önce oy kullanılmış, mevcut oyu döndür
            return ResponseEntity.ok(ApiResponse.success("Daha önce oy kullanılmış", response));
        }

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Oyunuz başarıyla kaydedildi", response));
    }

    // ==================== Oy Durumu ====================

    @Operation(summary = "Kullanıcının bu seçimdeki oy durumunu kontrol eder")
    @GetMapping("/my-status")
    public ResponseEntity<ApiResponse<VoteResponse>> getMyVoteStatus(
            @PathVariable Long electionId,
            @RequestHeader("X-User-Id") String userId) {

        VoteResponse response = voteService.getMyVoteStatus(electionId, userId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    // ==================== İstatistikler ====================

    @Operation(summary = "Seçim oy istatistiklerini döndürür (CLOSED/ARCHIVED seçimler veya seçim sahibi için)")
    @GetMapping("/stats")
    public ResponseEntity<ApiResponse<ElectionStatsResponse>> getElectionStats(
            @PathVariable Long electionId,
            @RequestHeader("X-User-Id") String userId) {

        ElectionStatsResponse response = voteService.getElectionStats(electionId, userId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
