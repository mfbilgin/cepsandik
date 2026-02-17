package com.cepsandik.electionservice.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

/**
 * RabbitMQ üzerinden user-service'e gönderilen bildirim mesajı.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ElectionNotificationMessage implements Serializable {

    /** Bildirim türü */
    private NotificationType type;

    /** Seçim bilgileri */
    private Long electionId;
    private String electionTitle;
    private Long communityId;

    /** Bildirim gönderilecek kullanıcı ID'leri */
    private List<String> targetUserIds;

    /** Ek bilgiler (hoursLeft, winnerName, totalVotes, vb.) */
    private Map<String, Object> metadata;

    public enum NotificationType {
        ELECTION_STARTED,
        ELECTION_REMINDER,
        ELECTION_ENDED,
        RESULTS_PUBLISHED
    }
}
