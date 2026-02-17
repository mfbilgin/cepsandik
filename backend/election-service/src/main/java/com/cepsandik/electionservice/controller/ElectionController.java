package com.cepsandik.electionservice.controller;

import com.cepsandik.electionservice.dto.request.CreateAccessCodeRequest;
import com.cepsandik.electionservice.dto.request.CreateCandidateRequest;
import com.cepsandik.electionservice.dto.request.CreateElectionRequest;
import com.cepsandik.electionservice.dto.request.UpdateElectionRequest;
import com.cepsandik.electionservice.dto.response.*;
import com.cepsandik.electionservice.service.ElectionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/elections")
@RequiredArgsConstructor
@Tag(name = "Elections", description = "Seçim yönetimi API'leri")
public class ElectionController {

    private final ElectionService electionService;

    // ==================== ELECTION CRUD ====================

    @Operation(summary = "Yeni seçim oluşturur")
    @PostMapping
    public ResponseEntity<ApiResponse<ElectionResponse>> createElection(
            @Valid @RequestBody CreateElectionRequest request,
            @RequestHeader("X-User-Id") String userId) {

        ElectionResponse response = electionService.createElection(request, userId);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Seçim başarıyla oluşturuldu", response));
    }

    @Operation(summary = "Seçim detaylarını getirir")
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<ElectionResponse>> getElection(@PathVariable Long id) {
        ElectionResponse response = electionService.getElectionById(id);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @Operation(summary = "Seçimi günceller")
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<ElectionResponse>> updateElection(
            @PathVariable Long id,
            @Valid @RequestBody UpdateElectionRequest request,
            @RequestHeader("X-User-Id") String userId) {

        ElectionResponse response = electionService.updateElection(id, request, userId);
        return ResponseEntity.ok(ApiResponse.success("Seçim güncellendi", response));
    }

    @Operation(summary = "Seçimi siler")
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteElection(
            @PathVariable Long id,
            @RequestHeader("X-User-Id") String userId) {

        electionService.deleteElection(id, userId);
        return ResponseEntity.ok(ApiResponse.success("Seçim silindi"));
    }

    // ==================== STATUS MANAGEMENT ====================

    @Operation(summary = "Seçimi yayınlar (DRAFT → SCHEDULED)")
    @PostMapping("/{id}/publish")
    public ResponseEntity<ApiResponse<ElectionResponse>> publishElection(
            @PathVariable Long id,
            @RequestHeader("X-User-Id") String userId) {

        ElectionResponse response = electionService.publishElection(id, userId);
        return ResponseEntity.ok(ApiResponse.success("Seçim yayınlandı", response));
    }

    @Operation(summary = "Seçimi başlatır (SCHEDULED → ACTIVE)")
    @PostMapping("/{id}/start")
    public ResponseEntity<ApiResponse<ElectionResponse>> startElection(
            @PathVariable Long id,
            @RequestHeader("X-User-Id") String userId) {

        ElectionResponse response = electionService.startElection(id, userId);
        return ResponseEntity.ok(ApiResponse.success("Seçim başlatıldı", response));
    }

    @Operation(summary = "Seçimi sonlandırır (ACTIVE → CLOSED)")
    @PostMapping("/{id}/end")
    public ResponseEntity<ApiResponse<ElectionResponse>> endElection(
            @PathVariable Long id,
            @RequestHeader("X-User-Id") String userId) {

        ElectionResponse response = electionService.endElection(id, userId);
        return ResponseEntity.ok(ApiResponse.success("Seçim sonlandırıldı", response));
    }

    @Operation(summary = "Seçimi iptal eder")
    @PostMapping("/{id}/cancel")
    public ResponseEntity<ApiResponse<ElectionResponse>> cancelElection(
            @PathVariable Long id,
            @RequestHeader("X-User-Id") String userId) {

        ElectionResponse response = electionService.cancelElection(id, userId);
        return ResponseEntity.ok(ApiResponse.success("Seçim iptal edildi", response));
    }

    // ==================== CANDIDATE MANAGEMENT ====================

    @Operation(summary = "Seçime aday ekler")
    @PostMapping("/{id}/candidates")
    public ResponseEntity<ApiResponse<CandidateResponse>> addCandidate(
            @PathVariable Long id,
            @Valid @RequestBody CreateCandidateRequest request,
            @RequestHeader("X-User-Id") String userId) {

        CandidateResponse response = electionService.addCandidate(id, request, userId);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Aday eklendi", response));
    }

    @Operation(summary = "Seçimin adaylarını listeler")
    @GetMapping("/{id}/candidates")
    public ResponseEntity<ApiResponse<List<CandidateResponse>>> getCandidates(@PathVariable Long id) {
        List<CandidateResponse> response = electionService.getCandidates(id);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @Operation(summary = "Adayı günceller")
    @PutMapping("/{id}/candidates/{candidateId}")
    public ResponseEntity<ApiResponse<CandidateResponse>> updateCandidate(
            @PathVariable Long id,
            @PathVariable Long candidateId,
            @Valid @RequestBody CreateCandidateRequest request,
            @RequestHeader("X-User-Id") String userId) {

        CandidateResponse response = electionService.updateCandidate(id, candidateId, request, userId);
        return ResponseEntity.ok(ApiResponse.success("Aday güncellendi", response));
    }

    @Operation(summary = "Adayı siler")
    @DeleteMapping("/{id}/candidates/{candidateId}")
    public ResponseEntity<ApiResponse<Void>> removeCandidate(
            @PathVariable Long id,
            @PathVariable Long candidateId,
            @RequestHeader("X-User-Id") String userId) {

        electionService.removeCandidate(id, candidateId, userId);
        return ResponseEntity.ok(ApiResponse.success("Aday silindi"));
    }

    // ==================== ACCESS CODE MANAGEMENT ====================

    @Operation(summary = "Erişim kodu oluşturur")
    @PostMapping("/{id}/access-codes")
    public ResponseEntity<ApiResponse<AccessCodeResponse>> createAccessCode(
            @PathVariable Long id,
            @Valid @RequestBody CreateAccessCodeRequest request,
            @RequestHeader("X-User-Id") String userId) {

        AccessCodeResponse response = electionService.createAccessCode(id, request, userId);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Erişim kodu oluşturuldu", response));
    }

    @Operation(summary = "Erişim kodlarını listeler")
    @GetMapping("/{id}/access-codes")
    public ResponseEntity<ApiResponse<List<AccessCodeResponse>>> getAccessCodes(
            @PathVariable Long id,
            @RequestHeader("X-User-Id") String userId) {

        List<AccessCodeResponse> response = electionService.getAccessCodes(id, userId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @Operation(summary = "Erişim kodunu deaktive eder")
    @DeleteMapping("/{id}/access-codes/{codeId}")
    public ResponseEntity<ApiResponse<Void>> deactivateAccessCode(
            @PathVariable Long id,
            @PathVariable Long codeId,
            @RequestHeader("X-User-Id") String userId) {

        electionService.deactivateAccessCode(id, codeId, userId);
        return ResponseEntity.ok(ApiResponse.success("Erişim kodu deaktive edildi"));
    }

    // ==================== PREVIEW & ARCHIVE ====================

    @Operation(summary = "Seçim önizleme – yayınlamadan önce tüm bilgileri ve eksikleri gösterir")
    @GetMapping("/{id}/preview")
    public ResponseEntity<ApiResponse<ElectionPreviewResponse>> previewElection(
            @PathVariable Long id,
            @RequestHeader("X-User-Id") String userId) {

        ElectionPreviewResponse response = electionService.previewElection(id, userId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @Operation(summary = "Topluluk bazlı arşivlenmiş seçimleri listeler")
    @GetMapping("/communities/{communityId}/archived")
    public ResponseEntity<ApiResponse<PageResponse<ElectionResponse>>> getArchivedElections(
            @PathVariable Long communityId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        PageResponse<ElectionResponse> response = electionService.getArchivedElections(communityId, page, size);
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}

