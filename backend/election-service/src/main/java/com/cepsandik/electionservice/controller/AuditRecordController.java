package com.cepsandik.electionservice.controller;

import com.cepsandik.electionservice.service.AuditRecordService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Faz 1.3 — Bağımsız doğrulanabilir tam election record (PUBLIC).
 *
 * Kimlik doğrulaması gerektirmez: içerik tamamen şeffaflık verisidir
 * (şifreli oylar, manifest, context, joint key, tally proof, bulletin
 * hash-zinciri). Gizli hiçbir şey yok. Üçüncü taraf gözlemci bu endpoint'ten
 * indirip standart ElectionGuard verifier ile seçimi bağımsız doğrular.
 */
@RestController
@RequestMapping("/api/v1/elections/{electionId}/audit-record")
@RequiredArgsConstructor
@Tag(name = "Audit Record", description = "E2E-V bağımsız doğrulama için tam election record")
public class AuditRecordController {

    private final AuditRecordService auditRecordService;

    @Operation(summary = "Tam denetlenebilir election record'u döner (public, auth gerektirmez)")
    @GetMapping
    public ResponseEntity<Map<String, Object>> getAuditRecord(@PathVariable Long electionId) {
        return ResponseEntity.ok(auditRecordService.buildAuditRecord(electionId));
    }
}
