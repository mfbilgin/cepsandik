package com.cepsandik.userservice.service;

import com.cepsandik.userservice.config.RabbitMQConfig;
import com.cepsandik.userservice.dto.EmailMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Service;

/**
 * RabbitMQ kuyruğundan e-posta mesajlarını alıp işleyen consumer.
 * Başarısız mesajlar Dead Letter Queue'ya yönlendirilir.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EmailConsumer {

    private final EmailService emailService;

    /**
     * Kuyruktan gelen e-posta mesajlarını işler.
     * Hata durumunda exception fırlatılır ve mesaj DLQ'ya gider.
     */
    @RabbitListener(queues = RabbitMQConfig.EMAIL_QUEUE)
    public void processEmailMessage(EmailMessage message) {
        log.info("E-posta mesajı alındı: to={}, type={}", message.to(), message.type());

        try {
            switch (message.type()) {
                case VERIFICATION -> emailService.sendVerificationEmailDirect(
                        message.to(),
                        message.firstName(),
                        message.token());
                case PASSWORD_RESET -> emailService.sendPasswordResetEmailDirect(
                        message.to(),
                        message.firstName(),
                        message.token());
            }
            log.info("E-posta başarıyla işlendi: to={}, type={}", message.to(), message.type());
        } catch (Exception e) {
            log.error("E-posta işlenirken hata: to={}, type={}, error={}",
                    message.to(), message.type(), e.getMessage());
            throw e; // DLQ'ya gitmesi için exception'ı fırlat
        }
    }
}
