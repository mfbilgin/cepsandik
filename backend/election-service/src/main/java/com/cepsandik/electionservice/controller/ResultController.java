package com.cepsandik.electionservice.controller;

import com.cepsandik.electionservice.dto.response.ApiResponse;
import com.cepsandik.electionservice.dto.response.ElectionResultResponse;
import com.cepsandik.electionservice.service.ResultService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/elections/{electionId}/results")
@RequiredArgsConstructor
@Tag(name = "Election Results", description = "Seçim sonuçları hesaplama ve görüntüleme API'leri")
public class ResultController {

    private final ResultService resultService;

    @Operation(summary = "Seçim sonuçlarını hesaplar (sadece seçim sahibi)")
    @PostMapping("/calculate")
    public ResponseEntity<ApiResponse<ElectionResultResponse>> calculateResults(
            @PathVariable Long electionId,
            @RequestHeader("X-User-Id") String userId) {

        ElectionResultResponse response = resultService.calculateResults(electionId, userId);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Sonuçlar hesaplandı", response));
    }

    @Operation(summary = "Seçim sonuçlarını görüntüler")
    @GetMapping
    public ResponseEntity<ApiResponse<ElectionResultResponse>> getResults(
            @PathVariable Long electionId,
            @RequestHeader("X-User-Id") String userId) {

        ElectionResultResponse response = resultService.getResults(electionId, userId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @Operation(summary = "Sonuçları yayınlar ve seçimi arşivler (CLOSED → ARCHIVED)")
    @PostMapping("/publish")
    public ResponseEntity<ApiResponse<ElectionResultResponse>> publishResults(
            @PathVariable Long electionId,
            @RequestHeader("X-User-Id") String userId) {

        ElectionResultResponse response = resultService.publishResults(electionId, userId);
        return ResponseEntity.ok(ApiResponse.success("Sonuçlar yayınlandı, seçim arşivlendi", response));
    }
}
