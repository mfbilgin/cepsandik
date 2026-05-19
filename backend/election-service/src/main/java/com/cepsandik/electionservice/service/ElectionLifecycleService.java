package com.cepsandik.electionservice.service;

import com.cepsandik.electionservice.client.CommunityServiceClient;
import com.cepsandik.electionservice.client.CryptoEngineClient;
import com.cepsandik.electionservice.config.UtcClock;
import com.cepsandik.electionservice.entity.Candidate;
import com.cepsandik.electionservice.entity.Election;
import com.cepsandik.electionservice.entity.Vote;
import com.cepsandik.electionservice.enums.ElectionStatus;
import com.cepsandik.electionservice.grpc.ContestInfo;
import com.cepsandik.electionservice.grpc.GuardianRecord;
import com.cepsandik.electionservice.grpc.SetupElectionResponse;
import com.cepsandik.electionservice.grpc.TallyElectionResponse;
import com.cepsandik.electionservice.repository.CandidateRepository;
import com.cepsandik.electionservice.repository.ElectionRepository;
import com.cepsandik.electionservice.repository.TallySubmissionRepository;
import com.cepsandik.electionservice.repository.VoteRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

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
    private final CandidateRepository candidateRepository;
    private final CommunityServiceClient communityServiceClient;
    private final ElectionNotificationProducer notificationProducer;
    private final CryptoEngineClient cryptoEngineClient;
    private final BulletinOutboxService bulletinOutbox;
    private final DistributedTallyService distributedTallyService;
    private final TallySubmissionRepository tallySubmissionRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${app.guardian.count:3}")
    private int guardianCount;

    @Value("${app.guardian.quorum:2}")
    private int guardianQuorum;

    @Value("${app.guardian.dev-bypass:false}")
    private boolean guardianDevBypass;

    /**
     * Faz 4.15b — Stuck-tally grace/cap artık env-override edilebilir. Default
     * 5dk/8: TEK-NODE auto-tally için makul (saniyeler sürer, 5dk = gerçekten
     * takılı). DAĞITIK manuel tally bambaşka: guardian'lar ayrı cihazlarda
     * elle partial+challenge gönderir, pilotta 10-30dk normaldir. Bu yüzden
     * dağıtık deployment APP_TALLY_GRACE_MINUTES'i yüksek set etmeli
     * (docker-compose.uat.yaml + prod). Not: findStuckTallies yalnızca
     * tallySessionId IS NOT NULL seçer → SADECE dağıtık tally; auto-tally
     * (dev-bypass) session set etmez, bu sete hiç girmez.
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
                if (guardianDevBypass) {
                    // === Legacy: Server-side auto key ceremony (Sprint 4c trust model) ===
                    setupElectionGuard(election);
                    election.setStatus(ElectionStatus.ACTIVE);
                    electionRepository.save(election);
                    log.info("Seçim otomatik başlatıldı (dev-bypass auto-ceremony): id={}", election.getId());
                } else if (election.getElectionGuardContext() != null
                        && !election.getElectionGuardContext().isBlank()) {
                    // === True E2E-V (Sprint 5.A): distributed-setup endpoint zaten
                    //     CreateJointKey RPC çağırdı, context dolu — sadece status
                    //     güncelle. start_time geçtiğinde ACTIVE'e geçer.
                    election.setStatus(ElectionStatus.ACTIVE);
                    electionRepository.save(election);
                    log.info("Seçim otomatik başlatıldı (distributed setup hazır): id={}", election.getId());
                } else {
                    log.warn("Seçim başlatılamadı (distributed setup beklenir, electionGuardContext null): id={}",
                            election.getId());
                    continue;
                }

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

                if (guardianDevBypass) {
                    // Legacy server-side trust mode (Sprint 4c) — sunucu private key share'leri görür
                    autoTally(election);
                } else {
                    // Gerçek E2E-V (Sprint 5.C.2) — distributed 3-round threshold decryption
                    try {
                        distributedTallyService.startSessionForElection(election);
                    } catch (Exception e) {
                        log.error("Distributed tally session başlatma hatası: electionId={}",
                                election.getId(), e);
                    }
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

    /**
     * Dev-bypass tally: setup'ta üretilen guardianRecords JSON'unu doğrudan
     * Crypto-Engine'in `tallyElection` RPC'sine geri besler. Distributed
     * guardian akışı (Sprint 5) eklenene kadar UAT için kullanılır.
     *
     * İdempotency: DB-level `tallyProof IS NULL` filter'ı `findElectionsToEnd`
     * sorgusuna eklendi. Bu metot içindeki fresh-read kontrolü ek savunma.
     */
    private void autoTally(Election election) {
        Election fresh = electionRepository.findById(election.getId()).orElse(null);
        if (fresh != null && fresh.getTallyProof() != null && !fresh.getTallyProof().isBlank()) {
            log.info("autoTally atlandı (tally_proof zaten dolu, fresh-read): electionId={}", election.getId());
            return;
        }
        if (election.getGuardianRecords() == null || election.getGuardianRecords().isBlank()) {
            log.warn("autoTally atlandı (guardian_records yok): electionId={}", election.getId());
            return;
        }
        if (election.getElectionGuardContext() == null || election.getElectionManifest() == null) {
            log.warn("autoTally atlandı (context/manifest yok): electionId={}", election.getId());
            return;
        }

        try {
            List<String> ciphertextBallots = voteRepository.findByElectionId(election.getId()).stream()
                    .map(Vote::getEncryptedBallot)
                    .filter(b -> b != null && !b.isBlank())
                    .toList();

            if (ciphertextBallots.isEmpty()) {
                log.info("autoTally atlandı (oy yok): electionId={}", election.getId());
                return;
            }

            List<Map<String, String>> guardianList = objectMapper.readValue(
                    election.getGuardianRecords(),
                    new TypeReference<List<Map<String, String>>>() {}
            );
            List<GuardianRecord> guardianRecords = guardianList.stream()
                    .map(m -> GuardianRecord.newBuilder()
                            .setGuardianId(m.getOrDefault("guardian_id", ""))
                            .setSerializedGuardian(m.getOrDefault("serialized_guardian", ""))
                            .build())
                    .toList();

            long started = System.currentTimeMillis();
            TallyElectionResponse response = cryptoEngineClient.tallyElection(
                    election.getId().toString(),
                    election.getElectionGuardContext(),
                    election.getElectionManifest(),
                    ciphertextBallots,
                    guardianRecords,
                    guardianQuorum
            );
            long elapsed = System.currentTimeMillis() - started;

            election.setTallyProof(response.getTallyProof());

            List<Map<String, Object>> contests = response.getResultsList().stream()
                    .map(contest -> Map.<String, Object>of(
                            "contest_id", contest.getContestId(),
                            "selections", contest.getSelectionsList().stream()
                                    .map(sel -> Map.<String, Object>of(
                                            "selection_id", sel.getSelectionId(),
                                            "tally", sel.getTally()))
                                    .toList()))
                    .toList();
            String tallyResultsJson = objectMapper.writeValueAsString(contests);
            election.setTallyResults(tallyResultsJson);
            electionRepository.save(election);

            // Faz 1.1 — outbox: sessiz kayıp yok, sıralı + retry'lı + idempotent.
            bulletinOutbox.enqueue(
                    String.valueOf(election.getId()),
                    "TALLY_PUBLISHED",
                    null,
                    null,
                    tallyResultsJson,
                    true);

            log.info("autoTally OK: electionId={}, ballots={}, contests={}, elapsed={}ms",
                    election.getId(), ciphertextBallots.size(), contests.size(), elapsed);

        } catch (Exception e) {
            log.error("autoTally hatası: electionId={}", election.getId(), e);
            // Tally başarısız olsa bile seçim CLOSED kalır; manuel publishResults bloklanır
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

    // ==================== Crypto-Engine Key Ceremony ====================

    /**
     * ElectionGuard Key Ceremony — otomatik geçişlerde de çalışır.
     * Context, manifest ve guardian record'ları election entity'sine yazılır.
     */
    private void setupElectionGuard(Election election) {
        try {
            List<Candidate> candidates = candidateRepository
                    .findByElectionIdAndIsDeletedFalseOrderByDisplayOrderAsc(election.getId());

            ContestInfo contestInfo = ContestInfo.newBuilder()
                    .setContestId("contest_" + election.getId())
                    .addAllSelectionIds(
                            candidates.stream()
                                    .map(c -> "candidate_" + c.getId())
                                    .toList()
                    )
                    .setNumberElected(election.getMaxSelections() != null ? election.getMaxSelections() : 1)
                    .setName(election.getTitle())
                    .build();

            SetupElectionResponse cryptoResponse = cryptoEngineClient.setupElection(
                    String.valueOf(election.getId()),
                    guardianCount,
                    guardianQuorum,
                    List.of(contestInfo),
                    election.getStartTime().toString(),
                    election.getEndTime().toString()
            );

            election.setElectionGuardContext(cryptoResponse.getElectionGuardContext());
            election.setElectionManifest(cryptoResponse.getElectionManifest());
            election.setElectionPublicKey(cryptoResponse.getJointPublicKey());

            List<Object> guardianList = cryptoResponse.getGuardianRecordsList().stream()
                    .map(gr -> java.util.Map.of(
                            "guardian_id", gr.getGuardianId(),
                            "serialized_guardian", gr.getSerializedGuardian()
                    ))
                    .collect(Collectors.toList());
            election.setGuardianRecords(
                    new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(guardianList)
            );

            log.info("ElectionGuard key ceremony tamamlandı (otomatik): electionId={}",
                    election.getId());

        } catch (Exception e) {
            log.error("Crypto-Engine SetupElection hatası (otomatik): electionId={}",
                    election.getId(), e);
            // Key ceremony başarısız olsa bile seçim başlatılsın,
            // ancak şifreleme olmadan çalışacak
        }
    }
}
