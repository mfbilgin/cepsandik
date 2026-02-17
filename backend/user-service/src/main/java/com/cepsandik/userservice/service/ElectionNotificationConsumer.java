package com.cepsandik.userservice.service;

import com.cepsandik.userservice.dto.ElectionNotificationMessage;
import com.cepsandik.userservice.dto.ElectionNotificationMessage.NotificationType;
import com.cepsandik.userservice.models.User;
import com.cepsandik.userservice.repositories.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import jakarta.mail.internet.MimeMessage;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * RabbitMQ'dan seçim bildirim mesajlarını alıp email olarak gönderen consumer.
 * Election-service tarafından publish edilen mesajları dinler.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ElectionNotificationConsumer {

    private final UserRepository userRepository;
    private final JavaMailSender mailSender;
    private final TemplateEngine templateEngine;

    @Value("${app.mail.from:info@cepsandik.com}")
    private String fromEmail;

    @Value("${app.mail.from-name:CepSandık}")
    private String fromName;

    private static final String NOTIFICATION_QUEUE = "notification.election.queue";

    @RabbitListener(queues = NOTIFICATION_QUEUE)
    public void processElectionNotification(ElectionNotificationMessage message) {
        log.info("Seçim bildirimi alındı: type={}, electionId={}, hedef üye sayısı={}",
                message.getType(), message.getElectionId(),
                message.getTargetUserIds() != null ? message.getTargetUserIds().size() : 0);

        if (message.getTargetUserIds() == null || message.getTargetUserIds().isEmpty()) {
            log.warn("Hedef kullanıcı listesi boş, bildirim atlanıyor: electionId={}",
                    message.getElectionId());
            return;
        }

        try {
            // userId (String UUID) → User entity
            List<UUID> uuids = message.getTargetUserIds().stream()
                    .map(UUID::fromString)
                    .toList();

            List<User> users = userRepository.findAllById(uuids);

            if (users.isEmpty()) {
                log.warn("Hedef kullanıcılar bulunamadı: electionId={}", message.getElectionId());
                return;
            }

            // Bildirim türüne göre template ve subject belirle
            String templateName = getTemplateName(message.getType());
            String subject = getSubject(message.getType(), message.getElectionTitle());

            // Her kullanıcıya email gönder
            int successCount = 0;
            int failCount = 0;

            for (User user : users) {
                try {
                    Context context = buildContext(message, user);
                    String htmlContent = templateEngine.process(templateName, context);

                    sendHtmlEmail(user.getEmail(), subject, htmlContent);
                    successCount++;

                } catch (Exception e) {
                    failCount++;
                    log.error("Email gönderilemedi: userId={}, email={}, hata={}",
                            user.getId(), user.getEmail(), e.getMessage());
                }
            }

            log.info("Seçim bildirimi tamamlandı: type={}, electionId={}, başarılı={}, başarısız={}",
                    message.getType(), message.getElectionId(), successCount, failCount);

        } catch (Exception e) {
            log.error("Seçim bildirimi işlenirken hata: type={}, electionId={}, hata={}",
                    message.getType(), message.getElectionId(), e.getMessage());
            throw e; // DLQ'ya gitmesi için
        }
    }

    private String getTemplateName(NotificationType type) {
        return switch (type) {
            case ELECTION_STARTED -> "election-started";
            case ELECTION_REMINDER -> "election-reminder";
            case ELECTION_ENDED -> "election-ended";
            case RESULTS_PUBLISHED -> "election-ended"; // Aynı template, farklı mesaj
        };
    }

    private String getSubject(NotificationType type, String electionTitle) {
        return switch (type) {
            case ELECTION_STARTED -> "CepSandık - Yeni Seçim Başladı: " + electionTitle;
            case ELECTION_REMINDER -> "CepSandık - Oy Kullanmayı Unutmayın: " + electionTitle;
            case ELECTION_ENDED -> "CepSandık - Seçim Sonuçlandı: " + electionTitle;
            case RESULTS_PUBLISHED -> "CepSandık - Sonuçlar Yayınlandı: " + electionTitle;
        };
    }

    private Context buildContext(ElectionNotificationMessage message, User user) {
        Context context = new Context();
        context.setVariable("firstName", user.getFirstName());
        context.setVariable("electionTitle", message.getElectionTitle());
        context.setVariable("electionId", message.getElectionId());

        Map<String, Object> metadata = message.getMetadata();
        if (metadata != null) {
            if (metadata.containsKey("hoursLeft")) {
                context.setVariable("hoursLeft", metadata.get("hoursLeft"));
            }
            if (metadata.containsKey("totalVotes")) {
                context.setVariable("totalVotes", metadata.get("totalVotes"));
            }
        }

        // Bildirim türüne göre ek değişkenler
        context.setVariable("notificationType", message.getType().name());
        return context;
    }

    private void sendHtmlEmail(String to, String subject, String htmlContent) {
        try {
            MimeMessage mimeMessage = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true, "UTF-8");

            helper.setFrom(fromEmail, fromName);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(htmlContent, true);

            mailSender.send(mimeMessage);

        } catch (Exception e) {
            log.error("Email gönderim hatası: to={}, subject={}, hata={}", to, subject, e.getMessage());
            throw new RuntimeException("Email gönderilemedi: " + e.getMessage(), e);
        }
    }
}
