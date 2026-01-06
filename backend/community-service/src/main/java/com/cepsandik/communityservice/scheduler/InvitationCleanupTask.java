package com.cepsandik.communityservice.scheduler;

import com.cepsandik.communityservice.entity.CommunityInvitation;
import com.cepsandik.communityservice.repository.CommunityInvitationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class InvitationCleanupTask {

    private final CommunityInvitationRepository invitationRepository;

    /**
     * Her gün gece yarısı süresi dolmuş davetleri pasif yapar.
     * Cron: Saniye Dakika Saat GününAyı Ay HaftanınGünü
     */
    @Scheduled(cron = "0 0 0 * * ?")
    @Transactional
    public void cleanupExpiredInvitations() {
        log.info("Süresi dolmuş davetler temizleniyor...");

        List<CommunityInvitation> expiredInvitations = invitationRepository
                .findByExpiresAtBeforeAndIsActiveTrue(LocalDateTime.now());

        int count = 0;
        for (CommunityInvitation invitation : expiredInvitations) {
            invitation.setIsActive(false);
            invitationRepository.save(invitation);
            count++;
        }

        log.info("Temizleme tamamlandı. {} adet davet pasif yapıldı.", count);
    }

    /**
     * Her saatte maksimum kullanım sayısına ulaşmış davetleri pasif yapar.
     */
    @Scheduled(cron = "0 0 * * * ?")
    @Transactional
    public void cleanupMaxUsedInvitations() {
        log.info("Maksimum kullanıma ulaşmış davetler kontrol ediliyor...");

        List<CommunityInvitation> maxUsedInvitations = invitationRepository.findMaxUsedActiveInvitations();

        int count = 0;
        for (CommunityInvitation invitation : maxUsedInvitations) {
            invitation.setIsActive(false);
            invitationRepository.save(invitation);
            count++;
        }

        if (count > 0) {
            log.info("{} adet maksimum kullanıma ulaşmış davet pasif yapıldı.", count);
        }
    }
}
