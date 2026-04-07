package com.cepsandik.electionservice.service;

import com.cepsandik.electionservice.client.CommunityServiceClient;
import com.cepsandik.electionservice.config.UtcClock;
import com.cepsandik.electionservice.entity.Election;
import com.cepsandik.electionservice.enums.ElectionStatus;
import com.cepsandik.electionservice.repository.ElectionRepository;
import com.cepsandik.electionservice.repository.VoteRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * SCHEDULED → ACTIVE ve ACTIVE → CLOSED otomatik geçişleri.
 * Ayrı transaction ile çalışır; salt-okuma sorguları içinden güvenle çağrılabilir.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ElectionLifecycleService {

    private final UtcClock utcClock;
    private final ElectionRepository electionRepository;
    private final VoteRepository voteRepository;
    private final CommunityServiceClient communityServiceClient;
    private final ElectionNotificationProducer notificationProducer;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void processScheduledToActive() {
        var now = utcClock.instant();
        List<Election> electionsToStart = electionRepository
                .findElectionsToStart(ElectionStatus.SCHEDULED, now);

        for (Election election : electionsToStart) {
            try {
                election.setStatus(ElectionStatus.ACTIVE);
                electionRepository.save(election);

                log.info("Seçim otomatik başlatıldı: id={}, title={}",
                        election.getId(), election.getTitle());

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

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void processActiveToClosed() {
        var now = utcClock.instant();
        List<Election> electionsToEnd = electionRepository
                .findElectionsToEnd(ElectionStatus.ACTIVE, now);

        for (Election election : electionsToEnd) {
            try {
                election.setStatus(ElectionStatus.CLOSED);
                electionRepository.save(election);

                long totalVotes = voteRepository.countByElectionId(election.getId());

                log.info("Seçim otomatik sonlandırıldı: id={}, title={}, totalVotes={}",
                        election.getId(), election.getTitle(), totalVotes);

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

    /**
     * Okuma API'leri öncesi veya zamanlayıcıda çağrılır; gecikme olmadan güncel durumu yansıtır.
     */
    public void processAllDueTransitions() {
        processScheduledToActive();
        processActiveToClosed();
    }
}
