package com.cepsandik.electionservice.controller;

import com.cepsandik.electionservice.dto.response.ApiResponse;
import com.cepsandik.electionservice.dto.response.MyElectionResponse;
import com.cepsandik.electionservice.service.MyElectionsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/elections/my")
@RequiredArgsConstructor
@Tag(name = "My Elections", description = "Kullanıcının seçim katılım bilgileri")
public class MyElectionsController {

    private final MyElectionsService myElectionsService;

    @Operation(summary = "Kullanıcının dahil olduğu aktif seçimleri listeler")
    @GetMapping("/active")
    public ResponseEntity<ApiResponse<List<MyElectionResponse>>> getMyActiveElections(
            @RequestHeader("X-User-Id") String userId) {

        List<MyElectionResponse> response = myElectionsService.getMyActiveElections(userId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @Operation(summary = "Kullanıcının geçmiş seçim katılımlarını listeler")
    @GetMapping("/history")
    public ResponseEntity<ApiResponse<List<MyElectionResponse>>> getMyVotingHistory(
            @RequestHeader("X-User-Id") String userId) {

        List<MyElectionResponse> response = myElectionsService.getMyVotingHistory(userId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
