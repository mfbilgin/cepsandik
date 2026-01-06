package com.cepsandik.userservice.service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

/**
 * E-posta gönderim servisi
 * AWS SES üzerinden info@cepsandik.com adresiyle e-posta gönderir.
 * 
 * NOT: Bu servis artık EmailConsumer tarafından çağrılıyor.
 * Doğrudan kullanım için EmailProducer'ı tercih edin.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EmailService {

    private final JavaMailSender mailSender;
    private final TemplateEngine templateEngine;

    @Value("${app.mail.from:info@cepsandik.com}")
    private String fromEmail;

    @Value("${app.mail.from-name:CepSandık}")
    private String fromName;

    @Value("${app.frontend.url:http://localhost:3000}")
    private String frontendUrl;

    /**
     * Kayıt doğrulama e-postası gönderir (senkron).
     * Bu metod EmailConsumer tarafından çağrılır.
     */
    public void sendVerificationEmailDirect(String toEmail, String firstName, String token) {
        String verificationUrl = frontendUrl + "/auth/verify/" + token;

        Context context = new Context();
        context.setVariable("firstName", firstName);
        context.setVariable("verificationUrl", verificationUrl);
        context.setVariable("expirationHours", 24);

        String htmlContent = templateEngine.process("email-verification", context);

        sendHtmlEmail(toEmail, "CepSandık - E-posta Adresinizi Doğrulayın", htmlContent);
    }

    /**
     * Parola sıfırlama e-postası gönderir (senkron).
     * Bu metod EmailConsumer tarafından çağrılır.
     */
    public void sendPasswordResetEmailDirect(String toEmail, String firstName, String token) {
        String resetUrl = frontendUrl + "/auth/reset-password/" + token;

        Context context = new Context();
        context.setVariable("firstName", firstName);
        context.setVariable("resetUrl", resetUrl);
        context.setVariable("expirationHours", 2);

        String htmlContent = templateEngine.process("password-reset", context);

        sendHtmlEmail(toEmail, "CepSandık - Parola Sıfırlama", htmlContent);
    }

    /**
     * Eski email adresine değişiklik bildirimi gönderir.
     */
    public void sendEmailChangeNotification(String oldEmail, String firstName, String newEmail) {
        Context context = new Context();
        context.setVariable("firstName", firstName);
        context.setVariable("oldEmail", oldEmail);
        context.setVariable("newEmail", newEmail);

        String htmlContent = templateEngine.process("email-change-notification", context);

        sendHtmlEmail(oldEmail, "CepSandık - Email Değişikliği Bildirimi", htmlContent);
    }

    /**
     * Yeni email adresine doğrulama linki gönderir.
     */
    public void sendEmailChangeVerification(String newEmail, String firstName, String token) {
        String confirmUrl = frontendUrl + "/users/confirm-email-change/" + token;

        Context context = new Context();
        context.setVariable("firstName", firstName);
        context.setVariable("newEmail", newEmail);
        context.setVariable("confirmUrl", confirmUrl);
        context.setVariable("expirationHours", 2);

        String htmlContent = templateEngine.process("email-change-verification", context);

        sendHtmlEmail(newEmail, "CepSandık - Yeni Email Adresinizi Doğrulayın", htmlContent);
    }

    /**
     * HTML formatında e-posta gönderir.
     * Hata durumunda exception fırlatır (DLQ için gerekli).
     */
    private void sendHtmlEmail(String to, String subject, String htmlContent) {
        if (to == null || subject == null || htmlContent == null) {
            throw new IllegalArgumentException("E-posta parametreleri null olamaz");
        }

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setFrom(fromEmail, fromName);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(htmlContent, true);

            mailSender.send(message);
            log.info("E-posta başarıyla gönderildi: to={}, subject={}", to, subject);

        } catch (MessagingException e) {
            log.error("E-posta oluşturulurken hata: to={}, error={}", to, e.getMessage());
            throw new RuntimeException("E-posta gönderilemedi: " + e.getMessage(), e);
        } catch (MailException e) {
            log.error("E-posta gönderilirken hata: to={}, error={}", to, e.getMessage());
            throw new RuntimeException("E-posta gönderilemedi: " + e.getMessage(), e);
        } catch (Exception e) {
            log.error("Beklenmeyen hata: to={}, error={}", to, e.getMessage());
            throw new RuntimeException("E-posta gönderilemedi: " + e.getMessage(), e);
        }
    }
}
