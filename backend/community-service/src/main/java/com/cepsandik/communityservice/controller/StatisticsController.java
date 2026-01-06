package com.cepsandik.communityservice.controller;

import com.cepsandik.communityservice.dto.response.ApiResponse;
import com.cepsandik.communityservice.dto.response.StatisticsResponse;
import com.cepsandik.communityservice.service.StatisticsService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/communities/{communityId}/statistics")
@RequiredArgsConstructor
public class StatisticsController {

    private final StatisticsService statisticsService;

    @GetMapping
    public ResponseEntity<ApiResponse<StatisticsResponse>> getCommunityStatistics(
            @PathVariable Long communityId,
            @RequestHeader("X-User-Id") String userId) {

        StatisticsResponse statistics = statisticsService.getCommunityStatistics(communityId, userId);
        return ResponseEntity.ok(ApiResponse.success(statistics));
    }
}
