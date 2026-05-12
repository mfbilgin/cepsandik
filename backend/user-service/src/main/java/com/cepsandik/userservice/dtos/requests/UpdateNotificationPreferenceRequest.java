package com.cepsandik.userservice.dtos.requests;

import com.cepsandik.userservice.models.NotificationPreference.NotificationCategory;
import com.cepsandik.userservice.models.NotificationPreference.NotificationChannel;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class UpdateNotificationPreferenceRequest {
    @NotNull
    private NotificationCategory category;
    
    @NotNull
    private NotificationChannel channel;
    
    @NotNull
    private Boolean enabled;
}
