package com.cepsandik.notificationservice.service;

import com.cepsandik.notificationservice.client.ExpoPushClient;
import com.cepsandik.notificationservice.client.UserServiceClient;
import com.cepsandik.notificationservice.dto.GuardianAssignmentEvent;
import com.cepsandik.notificationservice.dto.UserNotificationDetailsResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

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

    /**
     * Sandık görevlisi atamalarını işler. 
     * Tercihleri kontrol eder ve uygun kanallardan bildirim gönderir.
     */
    public void processGuardianAssignment(GuardianAssignmentEvent event) {
        log.info("Processing guardian assignment for election: {}", event.getElectionTitle());
        
        List<UserNotificationDetailsResponse> userDetails = userServiceClient.getUserDetails(event.getSelectedGuardianIds());
        
        for (UserNotificationDetailsResponse user : userDetails) {
            String category = event.getCategory(); // e.g. "GUARDIAN_DUTY"
            
            // 1. Push Bildirimi Gönderimi
            if (isChannelEnabled(user, category, "PUSH") && user.getPushToken() != null) {
                String title = "Sandık Görevlisi Seçildiniz";
                String body = String.format("%s topluluğunda %s seçimi için sandık görevlisi olarak seçildiniz. Lütfen anahtar üretimi için Sandık Görevlisi sekmesini kontrol ediniz.", 
                        event.getCommunityName(), event.getElectionTitle());
                
                expoPushClient.sendPushNotification(user.getPushToken(), title, body, Map.of(
                        "electionId", event.getElectionId(),
                        "type", "GUARDIAN_ASSIGNMENT"
                ));
            }

            // 2. Email Bildirimi Gönderimi
            if (isChannelEnabled(user, category, "EMAIL") && user.getEmail() != null) {
                String subject = "Sandık Görevlisi Görevi - " + event.getElectionTitle();
                String body = String.format("Sayın kullanıcımız,\n\n%s topluluğunda düzenlenen %s seçimi için sandık görevlisi olarak seçildiniz.\n\n" +
                        "Seçim güvenliğini sağlamak adına anahtar üretimi sürecine katılmanız gerekmektedir.\n\n" +
                        "Lütfen uygulamamızdaki 'Sandık Görevlisi' sekmesini ziyaret ediniz.\n\nİyi günler dileriz.", 
                        event.getCommunityName(), event.getElectionTitle());
                
                emailService.sendSimpleEmail(user.getEmail(), subject, body);
            }
        }
    }

    private boolean isChannelEnabled(UserNotificationDetailsResponse user, String category, String channel) {
        if (user.getPreferences() == null) return true; // Varsayılan: Açık
        Map<String, Boolean> catPrefs = user.getPreferences().get(category);
        if (catPrefs == null) return true; // Bu kategori için tercih yoksa: Açık
        return catPrefs.getOrDefault(channel, true);
    }
}
