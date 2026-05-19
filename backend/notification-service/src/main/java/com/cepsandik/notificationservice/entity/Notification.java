package com.cepsandik.notificationservice.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * Kullanıcının inbox bildirimi. ddl-auto=update ile Hibernate tabloyu otomatik
 * oluşturur. (user_id, created_at) üzerinde compound index — inbox sorgusu
 * "kullanıcının notification'ları, en yeniden eskiye" şeklinde olduğu için
 * pagination'ı tek index ile sıkı tutar.
 */
@Entity
@Table(
        name = "notifications",
        indexes = {
                @Index(name = "idx_notif_user_created", columnList = "user_id, created_at DESC"),
                @Index(name = "idx_notif_user_unread", columnList = "user_id, is_read")
        }
)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Notification {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(nullable = false, length = 2000)
    private String message;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private NotificationType type;

    @Column(name = "is_read", nullable = false)
    @Builder.Default
    private boolean isRead = false;

    /**
     * Bildirim seçim odaklı ise (örn. guardian assignment) seçim id'si.
     * Mobil tarafta deep-link / navigation için kullanılabilir.
     */
    @Column(name = "election_id")
    private Long electionId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = Instant.now();
    }
}
