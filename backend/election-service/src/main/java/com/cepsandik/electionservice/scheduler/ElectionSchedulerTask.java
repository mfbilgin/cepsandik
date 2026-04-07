package com.cepsandik.electionservice.scheduler;

import com.cepsandik.electionservice.config.UtcClock;
import com.cepsandik.electionservice.entity.Election;
import com.cepsandik.electionservice.repository.ElectionRepository;
import com.cepsandik.electionservice.repository.VoteTokenRepository;
import com.cepsandik.electionservice.client.CommunityServiceClient;
import com.cepsandik.electionservice.service.ElectionLifecycleService;
import com.cepsandik.electionservice.service.ElectionNotificationProducer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Seçim yaşam döngüsü zamanlanmış görevleri.
 *
 * - Otomatik başlatma / sonlandırma: {@link ElectionLifecycleService} (her dakika)
 * - Hatırlatma: Bitiş yaklaşan aktif seçimler (her saat)
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ElectionSchedulerTask {

    private final ElectionLifecycleService electionLifecycleService;
    private final ElectionRepository electionRepository;
    private final VoteTokenRepository voteTokenRepository;
    private final CommunityServiceClient communityServiceClient;
    private final ElectionNotificationProducer notificationProducer;
    private final UtcClock utcClock;

    private final Set<Long> recentlyRemindedElections = new HashSet<>();

    @Scheduled(fixedRate = 60000)
    public void runLifecycleTransitions() {
        electionLifecycleService.processAllDueTransitions();
    }

    @Scheduled(cron = "0 0 * * * ?")
    public void sendReminders() {
        Instant now = utcClock.instant();
        Instant threshold = now.plus(24, ChronoUnit.HOURS);

        List<Election> nearingEnd = electionRepository.findElectionsNearingEnd(now, threshold);

        for (Election election : nearingEnd) {
            if (recentlyRemindedElections.contains(election.getId())) {
                continue;
            }

            try {
                List<String> memberUserIds = communityServiceClient
                        .getMemberUserIds(election.getCommunityId());

                if (memberUserIds.isEmpty()) {
                    continue;
                }

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

        recentlyRemindedElections.removeIf(id -> nearingEnd.stream().noneMatch(e -> e.getId().equals(id)));
    }
}
