package com.cepsandik.userservice.dtos.responses;

import com.cepsandik.userservice.models.NotificationPreference.NotificationCategory;
import com.cepsandik.userservice.models.NotificationPreference.NotificationChannel;
import lombok.Builder;
import lombok.Data;

import java.util.Map;
import java.util.UUID;

@Data
@Builder
public class UserNotificationDetailsResponse {
    private UUID userId;
    private String email;
    private String pushToken;
    private Map<NotificationCategory, Map<NotificationChannel, Boolean>> preferences;
}
