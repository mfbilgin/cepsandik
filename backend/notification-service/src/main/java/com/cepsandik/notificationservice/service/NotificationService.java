package com.cepsandik.notificationservice.service;

import com.cepsandik.notificationservice.client.ExpoPushClient;
import com.cepsandik.notificationservice.client.UserServiceClient;
import com.cepsandik.notificationservice.dto.GuardianAssignmentEvent;
import com.cepsandik.notificationservice.dto.NotificationResponse;
import com.cepsandik.notificationservice.dto.UserNotificationDetailsResponse;
import com.cepsandik.notificationservice.entity.Notification;
import com.cepsandik.notificationservice.entity.NotificationType;
import com.cepsandik.notificationservice.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationService {

    private final UserServiceClient userServiceClient;
    private final ExpoPushClient expoPushClient;
    private final EmailService emailService;
    private final NotificationRepository notificationRepository;

    /**
     * Sandık görevlisi atamalarını işler.
     * - Push: kullanıcı PUSH'u açık bıraktıysa + token varsa gönderilir
     * - Email: kullanıcı EMAIL'i açık bıraktıysa + email varsa gönderilir
     * - Inbox persist: KOŞULSUZ. Inbox geçmişi tercihten bağımsızdır; ileride
     *   kullanıcı app'i açtığında görevlerinin kaydını görür. (Tercihler hangi
     *   kanaldan anlık bildirim gideceğini belirler, geçmişi değil.)
     */
    @Transactional
    public void processGuardianAssignment(GuardianAssignmentEvent event) {
        log.info("Processing guardian assignment for election: {}", event.getElectionTitle());

        List<UserNotificationDetailsResponse> userDetails = userServiceClient.getUserDetails(event.getSelectedGuardianIds());

        for (UserNotificationDetailsResponse user : userDetails) {
            String category = event.getCategory(); // e.g. "GUARDIAN_DUTY"

            String title = "Sandık Görevlisi Seçildiniz";
            String body = String.format(
                    "%s topluluğunda %s seçimi için sandık görevlisi olarak seçildiniz. Lütfen anahtar üretimi için Sandık Görevlisi sekmesini kontrol ediniz.",
                    event.getCommunityName(), event.getElectionTitle()
            );

            // 1. Push Bildirimi Gönderimi
            if (isChannelEnabled(user, category, "PUSH") && user.getPushToken() != null) {
                expoPushClient.sendPushNotification(user.getPushToken(), title, body, Map.of(
                        "electionId", event.getElectionId(),
                        "type", "GUARDIAN_ASSIGNMENT"
                ));
            }

            // 2. Email Bildirimi Gönderimi
            if (isChannelEnabled(user, category, "EMAIL") && user.getEmail() != null) {
                String subject = "Sandık Görevlisi Görevi - " + event.getElectionTitle();
                String emailBody = String.format(
                        "Sayın kullanıcımız,\n\n%s topluluğunda düzenlenen %s seçimi için sandık görevlisi olarak seçildiniz.\n\n" +
                        "Seçim güvenliğini sağlamak adına anahtar üretimi sürecine katılmanız gerekmektedir.\n\n" +
                        "Lütfen uygulamamızdaki 'Sandık Görevlisi' sekmesini ziyaret ediniz.\n\nİyi günler dileriz.",
                        event.getCommunityName(), event.getElectionTitle()
                );
                emailService.sendSimpleEmail(user.getEmail(), subject, emailBody);
            }

            // 3. Inbox persist (tercihten bağımsız — geçmiş kaydı)
            persistGuardianDuty(user.getUserId(), title, body, event.getElectionId());
        }
    }

    /**
     * Inbox'a tek bir GUARDIAN_DUTY bildirimi yazar. processGuardianAssignment
     * üzerinden çağrılır; ayrıca testler veya manuel tetiklemeler için
     * doğrudan da kullanılabilir.
     */
    @Transactional
    public Notification persistGuardianDuty(UUID userId, String title, String message, Long electionId) {
        if (userId == null) {
            log.warn("persistGuardianDuty: userId null — atlandı (electionId={})", electionId);
            return null;
        }
        Notification n = Notification.builder()
                .userId(userId)
                .title(title)
                .message(message)
                .type(NotificationType.GUARDIAN_DUTY)
                .electionId(electionId)
                .isRead(false)
                .build();
        return notificationRepository.save(n);
    }

    @Transactional(readOnly = true)
    public Page<NotificationResponse> getUserNotifications(UUID userId, Pageable pageable) {
        return notificationRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable)
                .map(NotificationResponse::from);
    }

    @Transactional(readOnly = true)
    public long countUnread(UUID userId) {
        return notificationRepository.countByUserIdAndIsReadFalse(userId);
    }

    /**
     * Tek bildirimi okundu işaretler. Bildirim başka kullanıcıya aitse veya
     * yoksa sessizce no-op (cross-user okuma sızıntısı oluşmasın diye 404
     * yerine 204 dönülür — yine de tercih bizim, controller'da 404'a çevirebiliriz).
     */
    @Transactional
    public boolean markAsRead(UUID userId, UUID notifId) {
        return notificationRepository.findByIdAndUserId(notifId, userId)
                .map(n -> {
                    if (!n.isRead()) {
                        n.setRead(true);
                        notificationRepository.save(n);
                    }
                    return true;
                })
                .orElse(false);
    }

    @Transactional
    public int markAllAsRead(UUID userId) {
        return notificationRepository.markAllReadByUserId(userId);
    }

    private boolean isChannelEnabled(UserNotificationDetailsResponse user, String category, String channel) {
        if (user.getPreferences() == null) return true; // Varsayılan: Açık
        Map<String, Boolean> catPrefs = user.getPreferences().get(category);
        if (catPrefs == null) return true; // Bu kategori için tercih yoksa: Açık
        return catPrefs.getOrDefault(channel, true);
    }
}
