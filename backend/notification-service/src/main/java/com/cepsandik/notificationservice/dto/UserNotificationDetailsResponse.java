package com.cepsandik.notificationservice.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserNotificationDetailsResponse {
    private UUID userId;
    private String email;
    private String pushToken;
    private Map<String, Map<String, Boolean>> preferences;
}
