package com.cepsandik.userservice.service;

import com.cepsandik.userservice.config.RabbitMQConfig;
import com.cepsandik.userservice.dto.EmailMessage;
import com.cepsandik.userservice.models.AuditLog;
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
    private final AuditService auditService;

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
            
            // Başarılı gönderimi veritabanına logla
            auditService.log(null, "MAIL", "SEND_" + message.type(), "SUCCESS", 
                AuditLog.LogLevel.INFO, "E-posta başarıyla gönderildi: " + message.to(), null);

        } catch (Exception e) {
            // Hatayı veritabanına logla
            auditService.log(null, "MAIL", "SEND_" + message.type(), "FAILURE", 
                AuditLog.LogLevel.ERROR, "E-posta gönderimi başarısız: " + message.to() + ". Hata: " + e.getMessage(), null);
            
            throw new org.springframework.amqp.AmqpRejectAndDontRequeueException(e);
        }
    }
}
