package com.cepsandik.electionservice.controller;

import com.cepsandik.electionservice.dto.request.SubmitDecryptionShareRequest;
import com.cepsandik.electionservice.dto.request.SubmitGuardianKeyRequest;
import com.cepsandik.electionservice.service.DistributedKeyCeremonyService;
import com.cepsandik.electionservice.service.DistributedTallyService;
import com.cepsandik.electionservice.service.GuardianService;
import java.util.List;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/guardians")
@RequiredArgsConstructor
@Tag(name = "Guardian Management", description = "Emanetçi (Guardian) işlemleri için API")
public class GuardianController {

    private final GuardianService guardianService;
    private final DistributedTallyService distributedTallyService;
    private final DistributedKeyCeremonyService keyCeremonyService;

    @GetMapping("/my-duties")
    @Operation(summary = "Giriş yapmış kullanıcının emanetçi olduğu seçimleri listeler")
    public ResponseEntity<?> getMyDuties(@RequestHeader("X-User-Id") String userId) {
        return ResponseEntity.ok(guardianService.getMyDuties(userId));
    }

    @PostMapping("/{electionId}/keys")
    @Operation(summary = "Emanetçi kamu anahtarını ve taahhütlerini yükler")
    public ResponseEntity<Void> submitPublicKey(
            @PathVariable Long electionId,
            @RequestHeader("X-User-Id") String userId,
            @Valid @RequestBody SubmitGuardianKeyRequest request) {
        
        guardianService.submitPublicKey(electionId, userId, request);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{electionId}/decrypt")
    @Operation(summary = "[DEPRECATED] Tek-shot decryption share yükle (Sprint 5.C.2 öncesi)")
    @Deprecated
    public ResponseEntity<Void> submitDecryptionShare(
            @PathVariable Long electionId,
            @RequestHeader("X-User-Id") String userId,
            @Valid @RequestBody SubmitDecryptionShareRequest request) {
        guardianService.submitDecryptionShare(electionId, userId, request);
        return ResponseEntity.ok().build();
    }

    // ============ Sprint 5.A.distributed — gerçek dağıtık key ceremony ============

    @GetMapping("/{electionId}/ceremony-info")
    @Operation(summary = "Guardian'ın ceremony parametreleri (guardianId, xCoordinate, n, q, status)")
    public ResponseEntity<Map<String, Object>> getCeremonyInfo(
            @PathVariable Long electionId,
            @RequestHeader("X-User-Id") String userId) {
        return ResponseEntity.ok(keyCeremonyService.getMyCeremonyInfo(electionId, userId));
    }

    @PostMapping("/{electionId}/public-key")
    @Operation(summary = "Round 1: dağıtık — full PublicKeysJson yükle (joint key tetiklemez)")
    public ResponseEntity<Void> submitDistributedPublicKey(
            @PathVariable Long electionId,
            @RequestHeader("X-User-Id") String userId,
            @RequestBody Map<String, String> body) {
        keyCeremonyService.submitPublicKey(electionId, userId, body.get("publicKeysJson"));
        return ResponseEntity.ok().build();
    }

    @GetMapping("/{electionId}/peer-public-keys")
    @Operation(summary = "Round 2: diğer guardian'ların PublicKeysJson'ları")
    public ResponseEntity<List<Map<String, Object>>> getPeerPublicKeys(
            @PathVariable Long electionId,
            @RequestHeader("X-User-Id") String userId) {
        return ResponseEntity.ok(keyCeremonyService.getPeerPublicKeys(electionId, userId));
    }

    @PostMapping("/{electionId}/encrypted-key-shares")
    @Operation(summary = "Round 2: peer'lara şifreli key share gönder (sunucu opak relay)")
    public ResponseEntity<Void> submitEncryptedKeyShares(
            @PathVariable Long electionId,
            @RequestHeader("X-User-Id") String userId,
            @RequestBody Map<String, Object> body) {
        @SuppressWarnings("unchecked")
        List<Map<String, String>> shares = (List<Map<String, String>>) body.get("shares");
        keyCeremonyService.submitEncryptedKeyShares(electionId, userId, shares);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/{electionId}/my-encrypted-key-shares")
    @Operation(summary = "Round 3: bana gelen şifreli key share'ler")
    public ResponseEntity<List<Map<String, Object>>> getMyEncryptedKeyShares(
            @PathVariable Long electionId,
            @RequestHeader("X-User-Id") String userId) {
        return ResponseEntity.ok(keyCeremonyService.getMyEncryptedKeyShares(electionId, userId));
    }

    @PostMapping("/{electionId}/ceremony-complete")
    @Operation(summary = "Round 3: ceremony-complete ack (tüm aktif READY → joint key)")
    public ResponseEntity<Map<String, Object>> ceremonyComplete(
            @PathVariable Long electionId,
            @RequestHeader("X-User-Id") String userId) {
        return ResponseEntity.ok(keyCeremonyService.markCeremonyComplete(electionId, userId));
    }

    @PostMapping("/{electionId}/decline")
    @Operation(summary = "Guardian 'bu seçime katılmıyorum' (cezasız, STATE_RESET tetikler)")
    public ResponseEntity<Map<String, Object>> declineCeremony(
            @PathVariable Long electionId,
            @RequestHeader("X-User-Id") String userId) {
        return ResponseEntity.ok(keyCeremonyService.declineCeremony(electionId, userId));
    }

    // ============ Sprint 5.C.2.5 — Distributed threshold decryption ============

    @GetMapping("/{electionId}/tally-task")
    @Operation(summary = "Tally task'ını pull et (sessionId + encrypted_tally + manifest)")
    public ResponseEntity<Map<String, Object>> getDecryptionTask(
            @PathVariable Long electionId,
            @RequestHeader("X-User-Id") String userId) {
        return ResponseEntity.ok(distributedTallyService.getDecryptionTask(electionId, userId));
    }

    @PostMapping("/{electionId}/partial-decryption")
    @Operation(summary = "Round 1: lokal partial decryption sonucunu yükle (body.guardianId opsiyonel; leader-mode'da trustee id)")
    public ResponseEntity<Map<String, Object>> submitPartialDecryption(
            @PathVariable Long electionId,
            @RequestHeader("X-User-Id") String userId,
            @RequestBody Map<String, String> body) {
        String partialsJson = body.get("partialsJson");
        String guardianId = body.get("guardianId"); // opsiyonel — leader-mode için
        if (partialsJson == null || partialsJson.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "partialsJson eksik"));
        }
        return ResponseEntity.ok(distributedTallyService.submitPartialDecryption(electionId, userId, partialsJson, guardianId));
    }

    @GetMapping("/{electionId}/challenges")
    @Operation(summary = "Round 2: challenges'ı pull et (long-poll, quorum dolana kadar bekler)")
    public ResponseEntity<Map<String, Object>> getChallenges(
            @PathVariable Long electionId,
            @RequestHeader("X-User-Id") String userId,
            @org.springframework.web.bind.annotation.RequestParam(required = false) String guardianId) {
        return ResponseEntity.ok(distributedTallyService.getChallenges(electionId, userId, guardianId));
    }

    @PostMapping("/{electionId}/challenge-response")
    @Operation(summary = "Round 3: lokal challenge response'unu yükle (body.guardianId opsiyonel)")
    public ResponseEntity<Map<String, Object>> submitChallengeResponse(
            @PathVariable Long electionId,
            @RequestHeader("X-User-Id") String userId,
            @RequestBody Map<String, String> body) {
        String responsesJson = body.get("responsesJson");
        String guardianId = body.get("guardianId");
        if (responsesJson == null || responsesJson.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "responsesJson eksik"));
        }
        return ResponseEntity.ok(distributedTallyService.submitChallengeResponse(electionId, userId, responsesJson, guardianId));
    }

    @PostMapping("/{electionId}/finalize-tally")
    @Operation(summary = "Leader-mode: owner explicit tally finalize (auto tetikleme yoksa kullan)")
    public ResponseEntity<Map<String, Object>> finalizeTally(
            @PathVariable Long electionId,
            @RequestHeader("X-User-Id") String userId) {
        return ResponseEntity.ok(distributedTallyService.finalizeByOwner(electionId, userId));
    }
}
