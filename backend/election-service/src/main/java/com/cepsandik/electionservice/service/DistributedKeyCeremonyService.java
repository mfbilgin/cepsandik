package com.cepsandik.electionservice.service;

import com.cepsandik.electionservice.client.BulletinBoardClient;
import com.cepsandik.electionservice.client.CryptoEngineClient;
import com.cepsandik.electionservice.entity.Candidate;
import com.cepsandik.electionservice.entity.Election;
import com.cepsandik.electionservice.entity.ElectionGuardian;
import com.cepsandik.electionservice.entity.GuardianKeyShare;
import com.cepsandik.electionservice.enums.ElectionStatus;
import com.cepsandik.electionservice.exception.ApiException;
import com.cepsandik.electionservice.grpc.ContestInfo;
import com.cepsandik.electionservice.grpc.CreateJointKeyResponse;
import com.cepsandik.electionservice.grpc.GuardianPublicKey;
import com.cepsandik.electionservice.repository.CandidateRepository;
import com.cepsandik.electionservice.repository.ElectionGuardianRepository;
import com.cepsandik.electionservice.repository.ElectionRepository;
import com.cepsandik.electionservice.repository.GuardianKeyShareRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Sprint 5.A.distributed — gerçek dağıtık key ceremony orkestrasyonu.
 *
 * Her guardian KENDİ cihazında 1 trustee taşır. Sunucu:
 *   - Round 1: PublicKeysJson topla (status KEY_UPLOADED)
 *   - Round 2: peer public key'leri dağıt + encrypted key share'leri opak relay
 *              (içeriği GÖREMEZ — HashedElGamal şifreli) (status KEYS_EXCHANGED)
 *   - Round 3: ceremony-complete ack (status READY)
 *   - Tüm aktif guardian READY → CreateJointKey RPC → context/manifest/jointKey
 *
 * KMP guardianId = "trustee{xCoordinate}" (ElectionGuardian.xCoordinate, 1-indexed).
 * STATE_RESET: bir guardian DECLINED/TIMEOUT olursa backup devreye girer, tüm
 * aktif guardian'lar PENDING'e döner, deadline T_backup uzar, K=2 cap → CANCELLED.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DistributedKeyCeremonyService {

    private final ElectionRepository electionRepository;
    private final ElectionGuardianRepository guardianRepository;
    private final GuardianKeyShareRepository keyShareRepository;
    private final CandidateRepository candidateRepository;
    private final CryptoEngineClient cryptoEngineClient;
    private final BulletinBoardClient bulletinBoardClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${app.guardian.quorum:2}")
    private int defaultQuorum;

    private static final int MAX_SUBSTITUTIONS = 2; // K=2 cap (v1.1 kararı)
    private static final long BACKUP_MIN_MINUTES = 15;
    private static final long BACKUP_MAX_MINUTES = 60;

    /** KMP guardianId şeması — ceremony boyunca tutarlı olmalı. */
    private String kmpGuardianId(ElectionGuardian g) {
        return "trustee" + g.getXCoordinate();
    }

    /**
     * Bulletin board şeffaflık logu — AUXILIARY. Hata ceremony'i bloklamamalı
     * (aksi halde @Transactional rollback olur, status kaydı geri alınır).
     * Legacy autoTally ile aynı fail-safe pattern.
     */
    private void safeBulletin(String electionId, String recordType,
                              String trackingCode, String ballotHash, String payload) {
        try {
            bulletinBoardClient.appendRecord(electionId, recordType, trackingCode, ballotHash, payload);
        } catch (Exception e) {
            log.warn("Bulletin board yazılamadı (ceremony devam eder): electionId={}, type={}, hata={}",
                    electionId, recordType, e.getMessage());
        }
    }

    private ElectionGuardian myGuardianOrThrow(Long electionId, String userId) {
        return guardianRepository.findByElectionIdAndUserId(electionId, UUID.fromString(userId))
                .orElseThrow(() -> ApiException.forbidden("Bu seçim için emanetçi değilsiniz"));
    }

    /** Aktif (DECLINED/TIMEOUT olmayan, yedek olmayan) asıl guardian listesi, xCoordinate sıralı. */
    private List<ElectionGuardian> activeGuardians(Long electionId) {
        return guardianRepository.findAllByElectionIdOrderByXCoordinateAsc(electionId).stream()
                .filter(g -> !g.isBackup())
                .filter(g -> g.getStatus() != ElectionGuardian.GuardianStatus.DECLINED
                        && g.getStatus() != ElectionGuardian.GuardianStatus.TIMEOUT)
                .toList();
    }

    // ==================== Mobile ceremony info ====================

    /** Guardian'ın kendi ceremony parametrelerini döner (mobile bunu kullanır). */
    @Transactional(readOnly = true)
    public Map<String, Object> getMyCeremonyInfo(Long electionId, String userId) {
        Election election = electionRepository.findById(electionId)
                .orElseThrow(() -> ApiException.notFound("Seçim bulunamadı"));
        ElectionGuardian me = myGuardianOrThrow(electionId, userId);
        List<ElectionGuardian> active = activeGuardians(electionId);
        int n = active.size();
        int q = election.getMinGuardiansThreshold() != null
                ? election.getMinGuardiansThreshold() : defaultQuorum;

        Map<String, Object> out = new java.util.HashMap<>();
        out.put("guardianId", kmpGuardianId(me));
        out.put("xCoordinate", me.getXCoordinate());
        out.put("n", n);
        out.put("q", q);
        out.put("status", me.getStatus().name());
        out.put("electionStatus", election.getStatus().name());
        out.put("setupDeadline", election.getSetupDeadline() != null
                ? election.getSetupDeadline().toString() : null);
        out.put("substitutionCount", election.getSubstitutionCount());
        return out;
    }

    // ==================== Round 1: public key submit ====================

    @Transactional
    public void submitPublicKey(Long electionId, String userId, String publicKeysJson) {
        Election election = electionRepository.findById(electionId)
                .orElseThrow(() -> ApiException.notFound("Seçim bulunamadı"));
        if (election.getStatus() != ElectionStatus.SCHEDULED) {
            throw ApiException.badRequest("Sadece SCHEDULED seçimler için anahtar yüklenebilir");
        }
        ElectionGuardian me = myGuardianOrThrow(electionId, userId);
        if (publicKeysJson == null || publicKeysJson.isBlank()) {
            throw ApiException.badRequest("publicKeysJson gerekli");
        }
        // Full PublicKeysJson coefficient_proofs kolonunda saklanır (TEXT).
        me.setCoefficientProofs(publicKeysJson);
        me.setStatus(ElectionGuardian.GuardianStatus.KEY_UPLOADED);
        guardianRepository.save(me);

        safeBulletin(String.valueOf(electionId),
                "KEY_UPLOADED", null, kmpGuardianId(me), null);
        log.info("Round1 KEY_UPLOADED: electionId={}, guardianId={}", electionId, kmpGuardianId(me));
    }

    // ==================== Round 2: peer keys + encrypted shares ====================

    /** Diğer guardian'ların PublicKeysJson'larını döner (status >= KEY_UPLOADED). */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> getPeerPublicKeys(Long electionId, String userId) {
        ElectionGuardian me = myGuardianOrThrow(electionId, userId);
        String myGid = kmpGuardianId(me);
        List<Map<String, Object>> peers = new ArrayList<>();
        for (ElectionGuardian g : activeGuardians(electionId)) {
            String gid = kmpGuardianId(g);
            if (gid.equals(myGid)) continue;
            if (g.getCoefficientProofs() == null || g.getCoefficientProofs().isBlank()) continue;
            peers.add(Map.of(
                    "guardianId", gid,
                    "publicKeysJson", g.getCoefficientProofs()));
        }
        return peers;
    }

    /** Bir guardian'ın peer'lara ürettiği şifreli key share'leri opak saklar. */
    @Transactional
    public void submitEncryptedKeyShares(Long electionId, String userId,
                                         List<Map<String, String>> shares) {
        Election election = electionRepository.findById(electionId)
                .orElseThrow(() -> ApiException.notFound("Seçim bulunamadı"));
        if (election.getStatus() != ElectionStatus.SCHEDULED) {
            throw ApiException.badRequest("Sadece SCHEDULED seçimler için key share gönderilebilir");
        }
        ElectionGuardian me = myGuardianOrThrow(electionId, userId);
        String fromGid = kmpGuardianId(me);
        if (shares == null || shares.isEmpty()) {
            throw ApiException.badRequest("shares gerekli");
        }
        for (Map<String, String> s : shares) {
            String toGid = s.get("toGuardianId");
            String enc = s.get("encryptedKeyShareJson");
            if (toGid == null || enc == null || toGid.isBlank() || enc.isBlank()) {
                throw ApiException.badRequest("share içinde toGuardianId/encryptedKeyShareJson eksik");
            }
            if (keyShareRepository.existsByElectionIdAndFromGuardianIdAndToGuardianId(
                    electionId, fromGid, toGid)) {
                continue; // idempotent
            }
            keyShareRepository.save(GuardianKeyShare.builder()
                    .electionId(electionId)
                    .fromGuardianId(fromGid)
                    .toGuardianId(toGid)
                    .encryptedShareJson(enc)
                    .build());
        }
        me.setStatus(ElectionGuardian.GuardianStatus.KEYS_EXCHANGED);
        guardianRepository.save(me);
        log.info("Round2 KEYS_EXCHANGED: electionId={}, from={}, shares={}",
                electionId, fromGid, shares.size());
    }

    /** Guardian'a gelen şifreli key share'leri döner (sadece kendisi çözebilir). */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> getMyEncryptedKeyShares(Long electionId, String userId) {
        ElectionGuardian me = myGuardianOrThrow(electionId, userId);
        String myGid = kmpGuardianId(me);
        return keyShareRepository.findByElectionIdAndToGuardianId(electionId, myGid).stream()
                .map(ks -> Map.<String, Object>of(
                        "fromGuardianId", ks.getFromGuardianId(),
                        "encryptedKeyShareJson", ks.getEncryptedShareJson()))
                .toList();
    }

    // ==================== Round 3: ceremony-complete ack ====================

    @Transactional
    public Map<String, Object> markCeremonyComplete(Long electionId, String userId) {
        Election election = electionRepository.findById(electionId)
                .orElseThrow(() -> ApiException.notFound("Seçim bulunamadı"));
        if (election.getStatus() != ElectionStatus.SCHEDULED) {
            throw ApiException.badRequest("Sadece SCHEDULED seçimler için ceremony tamamlanabilir");
        }
        ElectionGuardian me = myGuardianOrThrow(electionId, userId);
        me.setStatus(ElectionGuardian.GuardianStatus.READY);
        guardianRepository.save(me);
        safeBulletin(String.valueOf(electionId),
                "JOINT_KEY_GENERATED", null, kmpGuardianId(me), null);

        List<ElectionGuardian> active = activeGuardians(electionId);
        long readyCount = active.stream()
                .filter(g -> g.getStatus() == ElectionGuardian.GuardianStatus.READY)
                .count();
        log.info("Round3 READY: electionId={}, guardianId={}, ready={}/{}",
                electionId, kmpGuardianId(me), readyCount, active.size());

        boolean jointKeyGenerated = false;
        if (readyCount == active.size() && active.size() >= 2) {
            generateJointKey(election, active);
            jointKeyGenerated = true;
        }
        return Map.of(
                "status", me.getStatus().name(),
                "readyCount", readyCount,
                "total", active.size(),
                "jointKeyGenerated", jointKeyGenerated);
    }

    /**
     * Tüm aktif guardian READY → CreateJointKey RPC. guardianId="trustee{x}"
     * sırası xCoordinate'a göre; CreateJointKeyService bu sırayı korumalı.
     */
    private void generateJointKey(Election election, List<ElectionGuardian> guardians) {
        try {
            List<Candidate> candidates = candidateRepository
                    .findByElectionIdAndIsDeletedFalseOrderByDisplayOrderAsc(election.getId());
            if (candidates.isEmpty()) {
                throw ApiException.badRequest("Seçim için aday tanımlı değil");
            }
            ContestInfo contestInfo = ContestInfo.newBuilder()
                    .setContestId("contest_" + election.getId())
                    .addAllSelectionIds(candidates.stream().map(c -> "candidate_" + c.getId()).toList())
                    .setNumberElected(election.getMaxSelections() != null ? election.getMaxSelections() : 1)
                    .setName(election.getTitle())
                    .build();

            List<GuardianPublicKey> gpKeys = new ArrayList<>();
            List<String> guardianIds = new ArrayList<>();
            for (ElectionGuardian g : guardians) {
                String gid = kmpGuardianId(g);
                gpKeys.add(GuardianPublicKey.newBuilder()
                        .setGuardianId(gid)
                        .setPublicKey("")
                        .setCommitments("")
                        .setCoefficientProofs(extractCoefficientProofs(g.getCoefficientProofs()))
                        .build());
                guardianIds.add(gid);
            }

            int n = guardians.size();
            int q = election.getMinGuardiansThreshold() != null
                    ? election.getMinGuardiansThreshold() : defaultQuorum;

            CreateJointKeyResponse response = cryptoEngineClient.createJointKey(
                    election.getId().toString(), n, q, gpKeys, List.of(contestInfo),
                    election.getStartTime() != null ? election.getStartTime().toString() : "",
                    election.getEndTime() != null ? election.getEndTime().toString() : "");

            election.setElectionPublicKey(response.getJointPublicKey());
            election.setElectionGuardContext(response.getElectionGuardContext());
            election.setElectionManifest(response.getElectionManifest());
            election.setParticipatingGuardianIds(objectMapper.writeValueAsString(guardianIds));
            // status SCHEDULED kalır; ElectionLifecycleService start_time'da ACTIVE'e çeker
            electionRepository.save(election);

            safeBulletin(String.valueOf(election.getId()),
                    "JOINT_KEY_GENERATED", null, null, response.getElectionManifest());
            log.info("Joint key üretildi (distributed): electionId={}, n={}, q={}, jointKey={}...",
                    election.getId(), n, q,
                    response.getJointPublicKey().substring(0,
                            Math.min(40, response.getJointPublicKey().length())));
        } catch (Exception e) {
            log.error("generateJointKey (distributed) hatası: electionId={}", election.getId(), e);
            throw new RuntimeException("Kriptografik seremoni başarısız: " + e.getMessage(), e);
        }
    }

    // ==================== Decline + STATE_RESET + adaptif yedek ====================

    /**
     * Guardian "bu seçime katılmıyorum" der (cezasız). Slot yedeğe aktarılır,
     * ceremony state sıfırlanır (STATE_RESET), deadline T_backup uzar.
     */
    @Transactional
    public Map<String, Object> declineCeremony(Long electionId, String userId) {
        Election election = electionRepository.findById(electionId)
                .orElseThrow(() -> ApiException.notFound("Seçim bulunamadı"));
        if (election.getStatus() != ElectionStatus.SCHEDULED) {
            throw ApiException.badRequest("Sadece SCHEDULED seçimde red edilebilir");
        }
        ElectionGuardian me = myGuardianOrThrow(electionId, userId);
        me.setStatus(ElectionGuardian.GuardianStatus.DECLINED);
        guardianRepository.save(me);
        safeBulletin(String.valueOf(electionId),
                "GUARDIAN_DECLINED", null, kmpGuardianId(me), null);
        log.info("GUARDIAN_DECLINED: electionId={}, guardianId={}", electionId, kmpGuardianId(me));

        return substituteAndReset(election, me);
    }

    /**
     * STATE_RESET: backup devreye girer, tüm aktif guardian'lar PENDING'e döner
     * (KMP N-of-N — partial state korunamaz), deadline T_backup uzar.
     * K=2 substitution cap aşılırsa election CANCELLED.
     */
    @Transactional
    public Map<String, Object> substituteAndReset(Election election, ElectionGuardian leaving) {
        Long electionId = election.getId();

        int subCount = election.getSubstitutionCount() != null ? election.getSubstitutionCount() : 0;
        if (subCount >= MAX_SUBSTITUTIONS) {
            election.setStatus(ElectionStatus.CANCELLED);
            electionRepository.save(election);
            safeBulletin(String.valueOf(electionId),
                    "MAX_SUBSTITUTIONS_REACHED", null, null, null);
            log.warn("MAX_SUBSTITUTIONS_REACHED → CANCELLED: electionId={}, subCount={}",
                    electionId, subCount);
            return Map.of("cancelled", true, "substitutionCount", subCount,
                    "reason", "K=" + MAX_SUBSTITUTIONS + " substitution cap aşıldı");
        }

        // 1. Sıradaki backup'ı asıl guardian'a terfi et (leaving'in xCoordinate'ını al)
        List<ElectionGuardian> backups =
                guardianRepository.findBackupsByElectionId(electionId).stream()
                        .filter(b -> b.getStatus() == ElectionGuardian.GuardianStatus.PENDING)
                        .toList();
        if (backups.isEmpty()) {
            // Yedek yok → owner manuel müdahale (MANUAL_OVERRIDE), ceremony donar
            safeBulletin(String.valueOf(electionId),
                    "MANUAL_OVERRIDE", null, null, "Yedek havuzu tükendi");
            log.warn("Yedek yok, MANUAL_OVERRIDE: electionId={}", electionId);
            return Map.of("cancelled", false, "backupAvailable", false,
                    "message", "Yedek havuzu tükendi, owner müdahalesi gerekli");
        }
        ElectionGuardian backup = backups.get(0);
        backup.setBackup(false);
        backup.setXCoordinate(leaving.getXCoordinate()); // KMP guardianId tutarlılığı
        backup.setStatus(ElectionGuardian.GuardianStatus.PENDING);
        backup.setCoefficientProofs(null);
        guardianRepository.save(backup);

        // 2. Tüm aktif guardian'ları PENDING'e döndür (STATE_RESET) + public state temizle
        for (ElectionGuardian g : activeGuardians(electionId)) {
            g.setStatus(ElectionGuardian.GuardianStatus.PENDING);
            g.setCoefficientProofs(null);
            guardianRepository.save(g);
        }
        // 3. Relay'lenmiş şifreli share'leri sil (yeni peer set ile geçersiz)
        keyShareRepository.deleteByElectionId(electionId);

        // 4. substitution_count++ + deadline T_backup uzat
        int openSlots = 1; // bu reset'te 1 slot değişti
        long tBackupMin = computeBackupMinutes(election, openSlots);
        java.time.Instant base = election.getSetupDeadline() != null
                ? election.getSetupDeadline() : java.time.Instant.now();
        election.setSetupDeadline(base.plusSeconds(tBackupMin * 60));
        election.setSubstitutionCount(subCount + 1);
        electionRepository.save(election);

        safeBulletin(String.valueOf(electionId),
                "BACKUP_ASSIGNED", null, kmpGuardianId(backup), null);
        log.info("STATE_RESET: electionId={}, backup→trustee{}, subCount={}, +{}dk deadline",
                electionId, backup.getXCoordinate(), subCount + 1, tBackupMin);

        return Map.of("cancelled", false, "backupAvailable", true,
                "substitutionCount", subCount + 1,
                "backupGuardianId", kmpGuardianId(backup),
                "deadlineExtendedMinutes", tBackupMin);
    }

    /** T_backup = clamp(T_kalan / açık_slot, 15dk, 60dk) — v1.1 adaptif formül. */
    private long computeBackupMinutes(Election election, int openSlots) {
        java.time.Instant deadline = election.getSetupDeadline();
        long remainingMin = deadline != null
                ? Math.max(0, java.time.Duration.between(java.time.Instant.now(), deadline).toMinutes())
                : BACKUP_MAX_MINUTES;
        long perSlot = openSlots > 0 ? remainingMin / openSlots : remainingMin;
        return Math.max(BACKUP_MIN_MINUTES, Math.min(BACKUP_MAX_MINUTES, perSlot));
    }

    private String extractCoefficientProofs(String publicKeysJson) {
        try {
            JsonNode root = objectMapper.readTree(publicKeysJson);
            JsonNode coef = root.get("coefficientProofs");
            if (coef == null || coef.isNull()) {
                throw ApiException.badRequest("PublicKeysJson içinde coefficientProofs eksik");
            }
            return coef.toString();
        } catch (Exception e) {
            throw new RuntimeException("PublicKeysJson parse hatası: " + e.getMessage(), e);
        }
    }
}
