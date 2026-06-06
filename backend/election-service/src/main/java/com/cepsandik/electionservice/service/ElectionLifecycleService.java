package com.cepsandik.electionservice.service;

import com.cepsandik.electionservice.client.CommunityServiceClient;
import com.cepsandik.electionservice.config.UtcClock;
import com.cepsandik.electionservice.entity.Election;
import com.cepsandik.electionservice.enums.ElectionStatus;
import com.cepsandik.electionservice.repository.ElectionRepository;
import com.cepsandik.electionservice.repository.TallySubmissionRepository;
import com.cepsandik.electionservice.repository.VoteRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.temporal.ChronoUnit;
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
    private final BulletinOutboxService bulletinOutbox;
    private final DistributedTallyService distributedTallyService;
    private final TallySubmissionRepository tallySubmissionRepository;

    /**
     * Faz 4.15b — Stuck-tally grace/cap env-override edilebilir. Dağıtık manuel
     * tally pilotta 10-30dk normaldir; dağıtık deployment APP_TALLY_GRACE_MINUTES'i
     * yüksek set etmeli (docker-compose.uat.yaml + prod). findStuckTallies yalnızca
     * tallySessionId IS NOT NULL seçer → SADECE dağıtık tally bu sete girer.
     */
    @Value("${app.tally.grace-minutes:5}")
    private long tallyGraceMinutes;

    @Value("${app.tally.retry-cap:8}")
    private int tallyRetryCap;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void processScheduledToActive() {
        var now = utcClock.instant();
        List<Election> electionsToStart = electionRepository
                .findElectionsToStart(ElectionStatus.SCHEDULED, now);

        for (Election election : electionsToStart) {
            try {
                if (election.getElectionGuardContext() == null
                        || election.getElectionGuardContext().isBlank()) {
                    log.warn("Seçim başlatılamadı (distributed setup beklenir, electionGuardContext null): id={}",
                            election.getId());
                    continue;
                }
                // Distributed setup endpoint zaten CreateJointKey RPC çağırdı, context dolu.
                election.setStatus(ElectionStatus.ACTIVE);
                electionRepository.save(election);
                log.info("Seçim otomatik başlatıldı (distributed setup hazır): id={}", election.getId());

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

                // E2E-V (Sprint 5.C.2) — distributed 3-round threshold decryption
                try {
                    distributedTallyService.startSessionForElection(election);
                } catch (Exception e) {
                    log.error("Distributed tally session başlatma hatası: electionId={}",
                            election.getId(), e);
                }

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

    // ==================== Faz 2.8 — Stuck recovery ====================

    // Faz 4.15b: grace/cap artık @Value alanları (tallyGraceMinutes /
    // tallyRetryCap, yukarıda) — env-override edilebilir.

    /**
     * Süresi geçmiş, joint key'i hâlâ üretilmemiş ceremony'ler. Backup
     * substitution guardian-driven'dır; bu yalnızca güvenlik ağı: süresiz
     * SCHEDULED asılı kalmasın → CANCELLED + şeffaflık kaydı (owner yeniden
     * kurar). substitution_count zaten K=2 cap'i ayrı yönetir.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void processOverdueCeremonies() {
        var now = utcClock.instant();
        List<Election> overdue = electionRepository.findOverdueCeremonies(
                ElectionStatus.SCHEDULED, now);
        for (Election e : overdue) {
            try {
                e.setStatus(ElectionStatus.CANCELLED);
                electionRepository.save(e);
                bulletinOutbox.enqueue(String.valueOf(e.getId()), "CEREMONY_TIMEOUT",
                        null, null,
                        "Key ceremony setupDeadline aşıldı, joint key üretilemedi → CANCELLED",
                        true);
                log.warn("Ceremony süresi doldu, CANCELLED: electionId={}, setupDeadline={}",
                        e.getId(), e.getSetupDeadline());
            } catch (Exception ex) {
                log.error("processOverdueCeremonies hata: electionId={}", e.getId(), ex);
            }
        }
    }

    /**
     * Takılı tally'ler: CLOSED + session var ama tally_proof yok + grace
     * geçti. Faz 2.6 durable-replay ile tekrar dener; cap aşılınca sessizce
     * asılı bırakmaz → TALLY_FAILED + şeffaflık kaydı (owner müdahale eder).
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void processStuckTallies() {
        var graceBefore = utcClock.instant().minus(tallyGraceMinutes, ChronoUnit.MINUTES);
        List<Election> stuck = electionRepository.findStuckTallies(
                ElectionStatus.CLOSED, graceBefore);
        for (Election e : stuck) {
            try {
                // Faz 4.15b — Asıl doğruluk fix'i: hiç partial submission
                // yoksa bu tally "takılı" DEĞİL, guardian'ları bekliyor.
                // durable-replay'in replay edecek hiçbir şeyi yok (no-op),
                // ama eski kod yine de her 60sn retry sayacını artırıp cap'te
                // TALLY_FAILED yapıyordu → dağıtık manuel tally daha
                // başlamadan "başarısız" oluyordu. Replay yalnızca guardian'lar
                // partial gönderip session kaybolduğunda anlamlı (Faz 2.6/2.8
                // gerçek amacı). Submission yoksa atla — owner/operatör
                // izleyip gerekirse müdahale eder; sayaç yakılmaz.
                if (tallySubmissionRepository.countByElectionId(e.getId()) == 0) {
                    log.debug("Stuck-tally atlandı (henüz submission yok, "
                            + "guardian bekleniyor): electionId={}", e.getId());
                    continue;
                }
                int attempts = e.getTallyRetryCount() == null ? 0 : e.getTallyRetryCount();
                if (attempts >= tallyRetryCap) {
                    e.setStatus(ElectionStatus.TALLY_FAILED);
                    electionRepository.save(e);
                    bulletinOutbox.enqueue(String.valueOf(e.getId()), "TALLY_FAILED",
                            null, null,
                            "Tally " + attempts + " durable-replay denemesine rağmen "
                                    + "tamamlanamadı → TALLY_FAILED (owner müdahalesi)",
                            true);
                    log.error("Tally retry cap aşıldı → TALLY_FAILED: electionId={}, attempts={}",
                            e.getId(), attempts);
                    continue;
                }
                e.setTallyRetryCount(attempts + 1);
                electionRepository.save(e);
                log.warn("Stuck tally durable-replay denemesi #{}: electionId={}",
                        attempts + 1, e.getId());
                distributedTallyService.restartAndReplay(e);
            } catch (Exception ex) {
                log.error("processStuckTallies hata: electionId={}", e.getId(), ex);
            }
        }
    }

    /**
     * Okuma API'leri öncesi veya zamanlayıcıda çağrılır; gecikme olmadan güncel durumu yansıtır.
     */
    public void processAllDueTransitions() {
        processScheduledToActive();
        processActiveToClosed();
        processOverdueCeremonies();
        processStuckTallies();
    }

}
