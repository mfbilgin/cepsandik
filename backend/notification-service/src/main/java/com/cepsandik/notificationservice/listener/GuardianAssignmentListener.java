package com.cepsandik.notificationservice.listener;

import com.cepsandik.notificationservice.config.RabbitMQConfig;
import com.cepsandik.notificationservice.dto.GuardianAssignmentEvent;
import com.cepsandik.notificationservice.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@RequiredArgsConstructor
public class GuardianAssignmentListener {

    private final NotificationService notificationService;

    @RabbitListener(queues = RabbitMQConfig.ELECTION_NOTIFICATION_QUEUE)
    public void handleGuardianAssignment(GuardianAssignmentEvent event) {
        log.info("Received guardian assignment event for election: {}", event.getElectionId());
        try {
            notificationService.processGuardianAssignment(event);
        } catch (Exception e) {
            log.error("Error processing guardian assignment notification: ", e);
            // Kuyrukta kalması için exception fırlatılabilir (DLQ için)
            throw e;
        }
    }
}
