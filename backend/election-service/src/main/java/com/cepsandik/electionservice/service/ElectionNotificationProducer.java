package com.cepsandik.electionservice.service;

import com.cepsandik.electionservice.config.NotificationRabbitConfig;
import com.cepsandik.electionservice.dto.ElectionNotificationMessage;
import com.cepsandik.electionservice.dto.ElectionNotificationMessage.NotificationType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * Seçim bildirim mesajlarını RabbitMQ kuyruğuna gönderen servis.
 * Mesajlar user-service tarafından consume edilip email olarak gönderilir.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ElectionNotificationProducer {

    private final RabbitTemplate rabbitTemplate;

    /**
     * Seçim başladığında tüm topluluk üyelerine bildirim gönderir.
     */
    public void notifyElectionStarted(Long electionId, String title, Long communityId,
            List<String> memberUserIds) {
        ElectionNotificationMessage message = ElectionNotificationMessage.builder()
                .type(NotificationType.ELECTION_STARTED)
                .electionId(electionId)
                .electionTitle(title)
                .communityId(communityId)
                .targetUserIds(memberUserIds)
                .build();

        sendToQueue(message);
        log.info("Seçim başlangıç bildirimi kuyruğa eklendi: electionId={}, üye sayısı={}",
                electionId, memberUserIds.size());
    }

    /**
     * Oy kullanmamış üyelere hatırlatma bildirimi gönderir.
     */
    public void notifyElectionReminder(Long electionId, String title, Long communityId,
            List<String> nonVoterUserIds, long hoursLeft) {
        ElectionNotificationMessage message = ElectionNotificationMessage.builder()
                .type(NotificationType.ELECTION_REMINDER)
                .electionId(electionId)
                .electionTitle(title)
                .communityId(communityId)
                .targetUserIds(nonVoterUserIds)
                .metadata(Map.of("hoursLeft", hoursLeft))
                .build();

        sendToQueue(message);
        log.info("Hatırlatma bildirimi kuyruğa eklendi: electionId={}, oy kullanmamış={}, kalan saat={}",
                electionId, nonVoterUserIds.size(), hoursLeft);
    }

    /**
     * Seçim sonlandığında tüm topluluk üyelerine bildirim gönderir.
     */
    public void notifyElectionEnded(Long electionId, String title, Long communityId,
            List<String> memberUserIds, long totalVotes) {
        ElectionNotificationMessage message = ElectionNotificationMessage.builder()
                .type(NotificationType.ELECTION_ENDED)
                .electionId(electionId)
                .electionTitle(title)
                .communityId(communityId)
                .targetUserIds(memberUserIds)
                .metadata(Map.of("totalVotes", totalVotes))
                .build();

        sendToQueue(message);
        log.info("Seçim sonlandırma bildirimi kuyruğa eklendi: electionId={}, toplam oy={}",
                electionId, totalVotes);
    }

    /**
     * Sonuçlar yayınlandığında topluluk üyelerine bildirim gönderir.
     */
    public void notifyResultsPublished(Long electionId, String title, Long communityId,
            List<String> memberUserIds) {
        ElectionNotificationMessage message = ElectionNotificationMessage.builder()
                .type(NotificationType.RESULTS_PUBLISHED)
                .electionId(electionId)
                .electionTitle(title)
                .communityId(communityId)
                .targetUserIds(memberUserIds)
                .build();

        sendToQueue(message);
        log.info("Sonuç yayınlama bildirimi kuyruğa eklendi: electionId={}", electionId);
    }

    private void sendToQueue(ElectionNotificationMessage message) {
        rabbitTemplate.convertAndSend(
                NotificationRabbitConfig.NOTIFICATION_EXCHANGE,
                NotificationRabbitConfig.ELECTION_NOTIFICATION_ROUTING_KEY + "."
                        + message.getType().name().toLowerCase(),
                message);
    }
}
