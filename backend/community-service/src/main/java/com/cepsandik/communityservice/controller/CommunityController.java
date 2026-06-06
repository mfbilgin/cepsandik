package com.cepsandik.communityservice.controller;

import com.cepsandik.communityservice.dto.request.CreateCommunityRequest;
import com.cepsandik.communityservice.dto.request.UpdateCommunityRequest;
import com.cepsandik.communityservice.dto.response.ApiResponse;
import com.cepsandik.communityservice.dto.response.CommunityResponse;
import com.cepsandik.communityservice.dto.response.PageResponse;
import com.cepsandik.communityservice.service.CommunityService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/communities")
@RequiredArgsConstructor
public class CommunityController {

    private final CommunityService communityService;

    @PostMapping({ "", "/" })
    public ResponseEntity<ApiResponse<CommunityResponse>> createCommunity(
            @Valid @RequestBody CreateCommunityRequest request,
            @RequestHeader("X-User-Id") String userId) {

        CommunityResponse response = communityService.createCommunity(request, userId);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Topluluk başarıyla oluşturuldu", response));
    }

    @GetMapping({ "", "/" })
    public ResponseEntity<ApiResponse<PageResponse<CommunityResponse>>> getMyCommunities(
            @RequestHeader("X-User-Id") String userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        PageResponse<CommunityResponse> communities = communityService.getMyCommunities(userId, page, size);
        return ResponseEntity.ok(ApiResponse.success(communities));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<CommunityResponse>> getCommunityById(
            @PathVariable Long id,
            @RequestHeader("X-User-Id") String userId) {

        CommunityResponse response = communityService.getCommunityById(id, userId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<CommunityResponse>> updateCommunity(
            @PathVariable Long id,
            @Valid @RequestBody UpdateCommunityRequest request,
            @RequestHeader("X-User-Id") String userId) {

        CommunityResponse response = communityService.updateCommunity(id, request, userId);
        return ResponseEntity.ok(ApiResponse.success("Topluluk başarıyla güncellendi", response));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteCommunity(
            @PathVariable Long id,
            @RequestHeader("X-User-Id") String userId) {

        communityService.deleteCommunity(id, userId);
        return ResponseEntity.ok(ApiResponse.success("Topluluk başarıyla silindi", null));
    }

    @GetMapping("/discover")
    public ResponseEntity<ApiResponse<PageResponse<CommunityResponse>>> discoverCommunities(
            @RequestHeader("X-User-Id") String userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        PageResponse<CommunityResponse> communities = communityService.discoverCommunities(userId, page, size);
        return ResponseEntity.ok(ApiResponse.success(communities));
    }

    @PostMapping(value = "/{id}/logo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<CommunityResponse>> uploadLogo(
            @PathVariable Long id,
            @RequestParam("file") MultipartFile file,
            @RequestHeader("X-User-Id") String userId) {

        CommunityResponse response = communityService.uploadLogo(id, userId, file);
        return ResponseEntity.ok(ApiResponse.success("Topluluk logosu güncellendi", response));
    }

    @GetMapping("/unsplash")
    public ResponseEntity<ApiResponse<Object>> searchUnsplashImages(
            @RequestParam(defaultValue = "community") String query,
            @RequestHeader("X-User-Id") String userId) {
        
        Object response = communityService.searchUnsplashImages(query);
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
