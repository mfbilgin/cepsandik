package com.cepsandik.notificationservice.controller;

import com.cepsandik.notificationservice.dto.NotificationResponse;
import com.cepsandik.notificationservice.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;
import java.util.UUID;

/**
 * Inbox API. Gateway (OpenResty) JWT'yi doğrular, X-User-Id header'ını set
 * eder ve scope check yapar — bu controller header'a güvenir, ekstra auth
 * kontrolü yapmaz (microservice trust pattern).
 *
 * Endpoint'ler /v1/notifications altında mobile tarafından çağrılır;
 * nginx /v1/notifications → /api/v1/notifications şeklinde rewrite eder.
 */
@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
@Slf4j
public class NotificationController {

    private static final int MAX_PAGE_SIZE = 50;

    private final NotificationService notificationService;

    @GetMapping
    public Page<NotificationResponse> list(
            @RequestHeader("X-User-Id") String userIdHeader,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        UUID userId = parseUserId(userIdHeader);
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        Pageable pageable = PageRequest.of(Math.max(page, 0), safeSize);
        return notificationService.getUserNotifications(userId, pageable);
    }

    @GetMapping("/unread-count")
    public Map<String, Long> unreadCount(@RequestHeader("X-User-Id") String userIdHeader) {
        UUID userId = parseUserId(userIdHeader);
        return Map.of("count", notificationService.countUnread(userId));
    }

    @PutMapping("/{id}/read")
    public ResponseEntity<Void> markAsRead(
            @RequestHeader("X-User-Id") String userIdHeader,
            @PathVariable UUID id
    ) {
        UUID userId = parseUserId(userIdHeader);
        boolean ok = notificationService.markAsRead(userId, id);
        return ok ? ResponseEntity.noContent().build() : ResponseEntity.notFound().build();
    }

    @PutMapping("/read-all")
    public Map<String, Integer> markAllAsRead(@RequestHeader("X-User-Id") String userIdHeader) {
        UUID userId = parseUserId(userIdHeader);
        int updated = notificationService.markAllAsRead(userId);
        return Map.of("updated", updated);
    }

    private UUID parseUserId(String header) {
        if (header == null || header.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "X-User-Id header eksik");
        }
        try {
            return UUID.fromString(header);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Geçersiz X-User-Id formatı");
        }
    }
}
