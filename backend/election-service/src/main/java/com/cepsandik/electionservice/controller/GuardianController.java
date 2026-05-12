package com.cepsandik.electionservice.controller;

import com.cepsandik.electionservice.dto.request.SubmitDecryptionShareRequest;
import com.cepsandik.electionservice.dto.request.SubmitGuardianKeyRequest;
import com.cepsandik.electionservice.service.DistributedTallyService;
import com.cepsandik.electionservice.service.GuardianService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/guardians")
@RequiredArgsConstructor
@Tag(name = "Guardian Management", description = "Emanetçi (Guardian) işlemleri için API")
public class GuardianController {

    private final GuardianService guardianService;
    private final DistributedTallyService distributedTallyService;

    @GetMapping("/my-duties")
    @Operation(summary = "Giriş yapmış kullanıcının emanetçi olduğu seçimleri listeler")
    public ResponseEntity<?> getMyDuties(@AuthenticationPrincipal String userId) {
        return ResponseEntity.ok(guardianService.getMyDuties(userId));
    }

    @PostMapping("/{electionId}/keys")
    @Operation(summary = "Emanetçi kamu anahtarını ve taahhütlerini yükler")
    public ResponseEntity<Void> submitPublicKey(
            @PathVariable Long electionId,
            @AuthenticationPrincipal String userId,
            @Valid @RequestBody SubmitGuardianKeyRequest request) {
        
        guardianService.submitPublicKey(electionId, userId, request);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{electionId}/decrypt")
    @Operation(summary = "[DEPRECATED] Tek-shot decryption share yükle (Sprint 5.C.2 öncesi)")
    @Deprecated
    public ResponseEntity<Void> submitDecryptionShare(
            @PathVariable Long electionId,
            @AuthenticationPrincipal String userId,
            @Valid @RequestBody SubmitDecryptionShareRequest request) {
        guardianService.submitDecryptionShare(electionId, userId, request);
        return ResponseEntity.ok().build();
    }

    // ============ Sprint 5.C.2.5 — Distributed threshold decryption ============

    @GetMapping("/{electionId}/tally-task")
    @Operation(summary = "Tally task'ını pull et (sessionId + encrypted_tally + manifest)")
    public ResponseEntity<Map<String, Object>> getDecryptionTask(
            @PathVariable Long electionId,
            @AuthenticationPrincipal String userId) {
        return ResponseEntity.ok(distributedTallyService.getDecryptionTask(electionId, userId));
    }

    @PostMapping("/{electionId}/partial-decryption")
    @Operation(summary = "Round 1: lokal partial decryption sonucunu yükle")
    public ResponseEntity<Map<String, Object>> submitPartialDecryption(
            @PathVariable Long electionId,
            @AuthenticationPrincipal String userId,
            @RequestBody Map<String, String> body) {
        String partialsJson = body.get("partialsJson");
        if (partialsJson == null || partialsJson.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "partialsJson eksik"));
        }
        return ResponseEntity.ok(distributedTallyService.submitPartialDecryption(electionId, userId, partialsJson));
    }

    @GetMapping("/{electionId}/challenges")
    @Operation(summary = "Round 2: challenges'ı pull et (long-poll, quorum dolana kadar bekler)")
    public ResponseEntity<Map<String, Object>> getChallenges(
            @PathVariable Long electionId,
            @AuthenticationPrincipal String userId) {
        return ResponseEntity.ok(distributedTallyService.getChallenges(electionId, userId));
    }

    @PostMapping("/{electionId}/challenge-response")
    @Operation(summary = "Round 3: lokal challenge response'unu yükle (quorum dolarsa tally finalize)")
    public ResponseEntity<Map<String, Object>> submitChallengeResponse(
            @PathVariable Long electionId,
            @AuthenticationPrincipal String userId,
            @RequestBody Map<String, String> body) {
        String responsesJson = body.get("responsesJson");
        if (responsesJson == null || responsesJson.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "responsesJson eksik"));
        }
        return ResponseEntity.ok(distributedTallyService.submitChallengeResponse(electionId, userId, responsesJson));
    }
}
