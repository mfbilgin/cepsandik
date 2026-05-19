package com.cepsandik.userservice.dtos.responses;

import com.cepsandik.userservice.models.NotificationPreference;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Bildirim tercihi DTO'su. JPA entity'sini doğrudan serialize etmek
 * LAZY {@code user} alanı yüzünden LazyInitializationException / sonsuz
 * özyineleme ve parola-hash sızıntısına yol açıyordu; bu DTO yalnızca
 * istemcinin ihtiyaç duyduğu üç alanı taşır.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationPreferenceResponse {
    private NotificationPreference.NotificationCategory category;
    private NotificationPreference.NotificationChannel channel;
    private boolean enabled;

    public static NotificationPreferenceResponse from(NotificationPreference p) {
        return NotificationPreferenceResponse.builder()
                .category(p.getCategory())
                .channel(p.getChannel())
                .enabled(p.isEnabled())
                .build();
    }
}
