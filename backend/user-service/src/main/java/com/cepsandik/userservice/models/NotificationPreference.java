package com.cepsandik.userservice.models;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UuidGenerator;

import java.util.UUID;

@Entity
@Table(name = "notification_preferences")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationPreference {

    @Id
    @GeneratedValue
    @UuidGenerator
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private NotificationCategory category;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private NotificationChannel channel;

    @Column(name = "is_enabled", nullable = false)
    private boolean isEnabled = true;

    public enum NotificationCategory {
        GUARDIAN_DUTY,      // Sandık Görevlisi bildirimleri
        ELECTION_INFO,      // Seçim duyuruları
        COMMUNITY_NOTICE    // Topluluk haberleri
    }

    public enum NotificationChannel {
        PUSH,
        EMAIL
    }
}
