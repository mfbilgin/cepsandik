package com.cepsandik.electionservice.service;

import com.cepsandik.electionservice.client.BulletinBoardClient;
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
import com.cepsandik.electionservice.repository.VoteRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

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
    private final BulletinBoardClient bulletinBoardClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${app.guardian.count:3}")
    private int guardianCount;

    @Value("${app.guardian.quorum:2}")
    private int guardianQuorum;

    @Value("${app.guardian.dev-bypass:false}")
    private boolean guardianDevBypass;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void processScheduledToActive() {
        var now = utcClock.instant();
        List<Election> electionsToStart = electionRepository
                .findElectionsToStart(ElectionStatus.SCHEDULED, now);

        for (Election election : electionsToStart) {
            try {
                // === Crypto-Engine: ElectionGuard Key Ceremony ===
                setupElectionGuard(election);

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

                if (guardianDevBypass) {
                    autoTally(election);
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

            try {
                bulletinBoardClient.appendRecord(
                        String.valueOf(election.getId()),
                        "TALLY_PUBLISHED",
                        null,
                        null,
                        tallyResultsJson
                );
            } catch (Exception bbEx) {
                log.warn("Bulletin board TALLY_PUBLISHED yazılamadı: electionId={}, hata={}",
                        election.getId(), bbEx.getMessage());
            }

            log.info("autoTally OK: electionId={}, ballots={}, contests={}, elapsed={}ms",
                    election.getId(), ciphertextBallots.size(), contests.size(), elapsed);

        } catch (Exception e) {
            log.error("autoTally hatası: electionId={}", election.getId(), e);
            // Tally başarısız olsa bile seçim CLOSED kalır; manuel publishResults bloklanır
        }
    }

    /**
     * Okuma API'leri öncesi veya zamanlayıcıda çağrılır; gecikme olmadan güncel durumu yansıtır.
     */
    public void processAllDueTransitions() {
        processScheduledToActive();
        processActiveToClosed();
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
