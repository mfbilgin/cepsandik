package com.cepsandik.electionservice.controller;

import com.cepsandik.electionservice.dto.request.SubmitDecryptionShareRequest;
import com.cepsandik.electionservice.dto.request.SubmitGuardianKeyRequest;
import com.cepsandik.electionservice.service.GuardianService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/guardians")
@RequiredArgsConstructor
@Tag(name = "Guardian Management", description = "Emanetçi (Guardian) işlemleri için API")
public class GuardianController {

    private final GuardianService guardianService;

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
    @Operation(summary = "Seçim sonunda kısmi deşifre payını yükler")
    public ResponseEntity<Void> submitDecryptionShare(
            @PathVariable Long electionId,
            @AuthenticationPrincipal String userId,
            @Valid @RequestBody SubmitDecryptionShareRequest request) {
        
        guardianService.submitDecryptionShare(electionId, userId, request);
        return ResponseEntity.ok().build();
    }
}
