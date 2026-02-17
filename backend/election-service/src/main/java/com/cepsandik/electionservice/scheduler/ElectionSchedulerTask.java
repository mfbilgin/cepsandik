package com.cepsandik.electionservice.scheduler;

import com.cepsandik.electionservice.client.CommunityServiceClient;
import com.cepsandik.electionservice.entity.Election;
import com.cepsandik.electionservice.enums.ElectionStatus;
import com.cepsandik.electionservice.repository.ElectionRepository;
import com.cepsandik.electionservice.repository.VoteRepository;
import com.cepsandik.electionservice.repository.VoteTokenRepository;
import com.cepsandik.electionservice.service.ElectionNotificationProducer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Seçim yaşam döngüsü zamanlanmış görevleri.
 * 
 * - Otomatik başlatma: SCHEDULED → ACTIVE (her dakika)
 * - Hatırlatma: Bitiş yaklaşan aktif seçimler (her saat)
 * - Otomatik sonlandırma: ACTIVE → CLOSED (her dakika)
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ElectionSchedulerTask {

    private final ElectionRepository electionRepository;
    private final VoteTokenRepository voteTokenRepository;
    private final VoteRepository voteRepository;
    private final CommunityServiceClient communityServiceClient;
    private final ElectionNotificationProducer notificationProducer;

    /** Son hatırlatma gönderilen seçimler (tekrar göndermeyi önlemek için) */
    private final Set<Long> recentlyRemindedElections = new HashSet<>();

    // ==================== OTOMATİK BAŞLATMA ====================

    /**
     * Başlangıç zamanı gelmiş SCHEDULED seçimleri otomatik başlatır.
     * Her dakika çalışır.
     */
    @Scheduled(fixedRate = 60000)
    @Transactional
    public void autoStartElections() {
        LocalDateTime now = LocalDateTime.now();
        List<Election> electionsToStart = electionRepository.findElectionsToStart(now);

        for (Election election : electionsToStart) {
            try {
                election.setStatus(ElectionStatus.ACTIVE);
                electionRepository.save(election);

                log.info("Seçim otomatik başlatıldı: id={}, title={}",
                        election.getId(), election.getTitle());

                // Topluluk üyelerine bildirim gönder
                List<String> memberUserIds = communityServiceClient
                        .getMemberUserIds(election.getCommunityId());

                if (!memberUserIds.isEmpty()) {
                    notificationProducer.notifyElectionStarted(
                            election.getId(), election.getTitle(),
                            election.getCommunityId(), memberUserIds);
                }

            } catch (Exception e) {
                log.error("Seçim başlatma hatası: electionId={}, hata={}",
                        election.getId(), e.getMessage());
            }
        }
    }

    // ==================== HATIRLATMA ====================

    /**
     * Bitiş zamanı yaklaşan aktif seçimlerde oy kullanmamış üyelere hatırlatma
     * gönderir.
     * Her saat çalışır. Hatırlatma eşiği: 24 saat.
     */
    @Scheduled(cron = "0 0 * * * ?")
    public void sendReminders() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime threshold = now.plusHours(24);

        List<Election> nearingEnd = electionRepository.findElectionsNearingEnd(now, threshold);

        for (Election election : nearingEnd) {
            // Aynı seçime tekrar hatırlatma gönderme kontrolü
            if (recentlyRemindedElections.contains(election.getId())) {
                continue;
            }

            try {
                // Topluluk üyelerini al
                List<String> memberUserIds = communityServiceClient
                        .getMemberUserIds(election.getCommunityId());

                if (memberUserIds.isEmpty()) {
                    continue;
                }

                // Oy kullananları bul ve çıkar
                List<String> votedUserIds = voteTokenRepository
                        .findVotedUserIdsByElectionId(election.getId());

                List<String> nonVoterUserIds = new ArrayList<>(memberUserIds);
                nonVoterUserIds.removeAll(votedUserIds);

                if (nonVoterUserIds.isEmpty()) {
                    log.info("Tüm üyeler oy kullanmış, hatırlatma atlanıyor: electionId={}",
                            election.getId());
                    continue;
                }

                long hoursLeft = ChronoUnit.HOURS.between(now, election.getEndTime());

                notificationProducer.notifyElectionReminder(
                        election.getId(), election.getTitle(),
                        election.getCommunityId(), nonVoterUserIds, hoursLeft);

                recentlyRemindedElections.add(election.getId());

                log.info("Hatırlatma gönderildi: electionId={}, oy kullanmamış={}, kalan saat={}",
                        election.getId(), nonVoterUserIds.size(), hoursLeft);

            } catch (Exception e) {
                log.error("Hatırlatma gönderme hatası: electionId={}, hata={}",
                        election.getId(), e.getMessage());
            }
        }

        // Kapanmış seçimleri recently listesinden temizle
        recentlyRemindedElections.removeIf(id -> nearingEnd.stream().noneMatch(e -> e.getId().equals(id)));
    }

    // ==================== OTOMATİK SONLANDIRMA ====================

    /**
     * Bitiş zamanı gelmiş ACTIVE seçimleri otomatik sonlandırır.
     * Her dakika çalışır.
     */
    @Scheduled(fixedRate = 60000)
    @Transactional
    public void autoEndElections() {
        LocalDateTime now = LocalDateTime.now();
        List<Election> electionsToEnd = electionRepository.findElectionsToEnd(now);

        for (Election election : electionsToEnd) {
            try {
                election.setStatus(ElectionStatus.CLOSED);
                electionRepository.save(election);

                long totalVotes = voteRepository.countByElectionId(election.getId());

                log.info("Seçim otomatik sonlandırıldı: id={}, title={}, totalVotes={}",
                        election.getId(), election.getTitle(), totalVotes);

                // Topluluk üyelerine bildirim gönder
                List<String> memberUserIds = communityServiceClient
                        .getMemberUserIds(election.getCommunityId());

                if (!memberUserIds.isEmpty()) {
                    notificationProducer.notifyElectionEnded(
                            election.getId(), election.getTitle(),
                            election.getCommunityId(), memberUserIds, totalVotes);
                }

            } catch (Exception e) {
                log.error("Seçim sonlandırma hatası: electionId={}, hata={}",
                        election.getId(), e.getMessage());
            }
        }
    }
}
