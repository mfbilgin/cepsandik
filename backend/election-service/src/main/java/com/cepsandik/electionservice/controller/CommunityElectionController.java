package com.cepsandik.electionservice.controller;

import com.cepsandik.electionservice.dto.response.ApiResponse;
import com.cepsandik.electionservice.dto.response.ElectionResponse;
import com.cepsandik.electionservice.dto.response.PageResponse;
import com.cepsandik.electionservice.service.ElectionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/elections/community/{communityId}")
@RequiredArgsConstructor
@Tag(name = "Community Elections", description = "Topluluk seçimleri API'leri")
public class CommunityElectionController {

    private final ElectionService electionService;

    @Operation(summary = "Topluluk seçimlerini listeler")
    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<ElectionResponse>>> getCommunityElections(
            @PathVariable Long communityId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {

        PageResponse<ElectionResponse> response = electionService.getElectionsByCommunity(communityId, page, size);
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
