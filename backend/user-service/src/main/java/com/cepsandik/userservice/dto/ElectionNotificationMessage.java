package com.cepsandik.userservice.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

/**
 * Election-service'den gelen bildirim mesajı.
 * RabbitMQ üzerinden JSON olarak deserialize edilir.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ElectionNotificationMessage implements Serializable {

    private NotificationType type;
    private Long electionId;
    private String electionTitle;
    private Long communityId;
    private List<String> targetUserIds;
    private Map<String, Object> metadata;

    public enum NotificationType {
        ELECTION_STARTED,
        ELECTION_REMINDER,
        ELECTION_ENDED,
        RESULTS_PUBLISHED
    }
}
