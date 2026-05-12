package com.cepsandik.userservice.controllers;

import com.cepsandik.userservice.dtos.responses.UserResponse;
import com.cepsandik.userservice.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Servisler arası iletişim için dahili endpoint'ler.
 * Gateway üzerinden dışarıya açık DEĞİLDİR.
 */
@RestController
@RequestMapping("/internal/api/v1/users")
@RequiredArgsConstructor
public class InternalUserController {

    private final UserService userService;

    /**
     * Verilen ID listesindeki kullanıcıların bilgilerini döner.
     */
    @PostMapping("/batch")
    public ResponseEntity<List<UserResponse>> getUsersBatch(@RequestBody List<UUID> userIds) {
        List<UserResponse> users = userService.getUsersByIds(userIds);
        return ResponseEntity.ok(users);
    }

    /**
     * Verilen ID'ler arasından uygun emanetçileri filtreleyip ID olarak döner.
     */
    @PostMapping("/eligible-guardians")
    public ResponseEntity<List<UUID>> filterEligibleGuardians(@RequestBody List<UUID> userIds) {
        return ResponseEntity.ok(userService.filterEligibleGuardians(userIds));
    }

    /**
     * Tüm sistemden rastgele N adet uygun emanetçi seçer.
     */
    @GetMapping("/random-guardians")
    public ResponseEntity<List<UUID>> getRandomEligibleGuardians(@RequestParam int limit) {
        return ResponseEntity.ok(userService.getRandomEligibleGuardians(limit));
    }
    /**
     * Bildirim servisi için kullanıcı iletişim ve tercih detaylarını toplu döner.
     */
    @PostMapping("/notification-details")
    public ResponseEntity<List<com.cepsandik.userservice.dtos.responses.UserNotificationDetailsResponse>> getNotificationDetails(@RequestBody List<UUID> userIds) {
        return ResponseEntity.ok(userService.getNotificationDetails(userIds));
    }
}
