package com.cepsandik.notificationservice.dto;

import com.cepsandik.notificationservice.entity.Notification;

import java.time.Instant;
import java.util.UUID;

public record NotificationResponse(
        UUID id,
        String title,
        String message,
        String type,
        boolean isRead,
        Long electionId,
        Instant createdAt
) {
    public static NotificationResponse from(Notification n) {
        return new NotificationResponse(
                n.getId(),
                n.getTitle(),
                n.getMessage(),
                n.getType().name(),
                n.isRead(),
                n.getElectionId(),
                n.getCreatedAt()
        );
    }
}
