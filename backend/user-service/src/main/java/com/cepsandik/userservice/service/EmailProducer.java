package com.cepsandik.userservice.service;

import com.cepsandik.userservice.config.RabbitMQConfig;
import com.cepsandik.userservice.dto.EmailMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

/**
 * E-posta mesajlarını RabbitMQ kuyruğuna gönderen servis.
 * Mesajlar kuyruktan EmailConsumer tarafından alınıp işlenir.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EmailProducer {

    private final RabbitTemplate rabbitTemplate;

    /**
     * Doğrulama e-postası için mesaj kuyruğa ekler.
     */
    public void sendVerificationEmail(String to, String firstName, String token) {
        EmailMessage message = new EmailMessage(to, firstName, token, EmailMessage.EmailType.VERIFICATION);
        sendToQueue(message);
        log.info("Doğrulama e-postası kuyruğa eklendi: to={}", to);
    }

    /**
     * Parola sıfırlama e-postası için mesaj kuyruğa ekler.
     */
    public void sendPasswordResetEmail(String to, String firstName, String token) {
        EmailMessage message = new EmailMessage(to, firstName, token, EmailMessage.EmailType.PASSWORD_RESET);
        sendToQueue(message);
        log.info("Parola sıfırlama e-postası kuyruğa eklendi: to={}", to);
    }

    private void sendToQueue(EmailMessage message) {
        rabbitTemplate.convertAndSend(
                RabbitMQConfig.EMAIL_EXCHANGE,
                RabbitMQConfig.EMAIL_ROUTING_KEY,
                message);
    }
}
