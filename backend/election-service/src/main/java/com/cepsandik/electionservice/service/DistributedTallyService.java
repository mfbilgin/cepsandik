package com.cepsandik.electionservice.service;

import com.cepsandik.electionservice.client.CryptoEngineClient;
import com.cepsandik.electionservice.entity.Election;
import com.cepsandik.electionservice.entity.ElectionGuardian;
import com.cepsandik.electionservice.entity.Vote;
import com.cepsandik.electionservice.exception.ApiException;
import com.cepsandik.electionservice.grpc.GetChallengesResponse;
import com.cepsandik.electionservice.grpc.SessionAckResponse;
import com.cepsandik.electionservice.grpc.StartTallyDecryptionSessionResponse;
import com.cepsandik.electionservice.grpc.TallyElectionResponse;
import com.cepsandik.electionservice.entity.TallySubmission;
import com.cepsandik.electionservice.repository.ElectionGuardianRepository;
import com.cepsandik.electionservice.repository.ElectionRepository;
import com.cepsandik.electionservice.repository.TallySubmissionRepository;
import com.cepsandik.electionservice.repository.VoteRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Sprint 5.C.2.5 — Gerçek E2E-V threshold tally orchestrator (Java side).
 *
 * Election kapandığında {@link #startSessionForElection} çağrılır:
 *   1. KMP crypto-engine'in `StartTallyDecryptionSession` RPC'si tetiklenir
 *   2. Dönen sessionId + encrypted_tally_json Election entity'sine yazılır
 *   3. Mobile guardian'lar bu encrypted_tally üzerinden lokal KMP ile partial
 *      decryption + challenge response üretir, HTTP endpoint'leri üzerinden
 *      iletir
 *   4. Q (quorum) guardian CHALLENGE_RESPONSE_SUBMITTED durumuna gelince
 *      {@link #tryFinalize} otomatik tally'i açar (KMP DecryptorDoerre
 *      background thread'inde tamamlandığında server gelen finalize RPC
 *      cevabını döner).
 *
 * SUNUCU HİÇBİR guardian private key share'ini görmez — tüm crypto material
 * mobile cihazda kalır.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DistributedTallyService {

    private final ElectionRepository electionRepository;
    private final ElectionGuardianRepository guardianRepository;
    private final VoteRepository voteRepository;
    private final CryptoEngineClient cryptoEngineClient;
    private final BulletinOutboxService bulletinOutbox;
    private final TallySubmissionRepository tallySubmissionRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    // ============ Faz 2.6 — Kalıcı tally session (durable replay) ============

    /** Submission'ı kalıcılaştır (upsert: aynı guardian+round tekrar gönderirse güncelle). */
    private void persistSubmission(Long electionId, String guardianId, String round, String payloadJson) {
        TallySubmission s = tallySubmissionRepository
                .findByElectionIdAndGuardianIdAndRound(electionId, guardianId, round)
                .orElseGet(() -> TallySubmission.builder()
                        .electionId(electionId).guardianId(guardianId).round(round).build());
        s.setPayloadJson(payloadJson);
        tallySubmissionRepository.save(s);
    }

    /** crypto-engine session kayıp mı (restart/crash sonrası)? */
    private boolean isSessionLost(SessionAckResponse ack) {
        if (ack == null || ack.getAccepted()) return false;
        String e = ack.getError() == null ? "" : ack.getError().toLowerCase();
        return e.contains("session bulunam") || e.contains("session not found");
    }

    /**
     * crypto-engine session'ı kaybolduysa: YENİ session aç + persisted
     * partial/challenge-response'ları SIRAYLA replay et. Q-of-N seçimi
     * crypto-engine'de deterministik (guardianId sıralı ilk Q) olduğundan
     * Fiat-Shamir challenge'ı aynı üretilir → persisted response'lar geçerli.
     * Guardian (mobil) yeniden hesap YAPMAZ.
     */
    @Transactional
    public void restartAndReplay(Election election) {
        Long eid = election.getId();
        log.warn("Tally session kayıp/yenileniyor — durable replay: electionId={}", eid);
        election.setTallySessionId(null);
        electionRepository.save(election);
        // Fresh session (startSessionForElection sessionId null olunca üretir)
        startSessionForElection(election);
        Election fresh = electionRepository.findById(eid).orElseThrow();
        String sid = fresh.getTallySessionId();
        if (sid == null || sid.isBlank()) {
            log.error("restartAndReplay: yeni session üretilemedi, electionId={}", eid);
            return;
        }
        List<TallySubmission> partials = tallySubmissionRepository
                .findByElectionIdAndRoundOrderByIdAsc(eid, TallySubmission.ROUND_PARTIAL);
        List<TallySubmission> responses = tallySubmissionRepository
                .findByElectionIdAndRoundOrderByIdAsc(eid, TallySubmission.ROUND_CHALLENGE_RESPONSE);

        // Faz 2.7 — "tamamlanmış guardian" tercihi: app'i ölen guardian sadece
        // partial gönderip challenge-response göndermediyse, restart sonrası o
        // guardian quorum slotunu işgal edip tally'i kilitlememeli. Hem PARTIAL
        // hem CHALLENGE_RESPONSE'u olan guardian'lar "complete". Eğer >= quorum
        // complete guardian varsa SADECE onları replay et (crypto-engine
        // deterministik ilk-Q sıralı seçtiğinden hepsi complete → kilit yok).
        java.util.Set<String> respGuardians = responses.stream()
                .map(TallySubmission::getGuardianId)
                .collect(java.util.stream.Collectors.toSet());
        int quorum = election.getMinGuardiansThreshold() != null
                ? election.getMinGuardiansThreshold() : 2;
        List<TallySubmission> completePartials = partials.stream()
                .filter(p -> respGuardians.contains(p.getGuardianId()))
                .toList();
        List<TallySubmission> partialsToReplay =
                completePartials.size() >= quorum ? completePartials : partials;
        log.info("Durable replay: complete guardian={}, quorum={}, replay seti={} partial",
                completePartials.size(), quorum, partialsToReplay.size());

        for (TallySubmission p : partialsToReplay) {
            try {
                cryptoEngineClient.submitPartialDecryption(sid, p.getGuardianId(), p.getPayloadJson());
            } catch (Exception ex) {
                log.warn("replay partial FAIL guardian={}, hata={}", p.getGuardianId(), ex.getMessage());
            }
        }
        for (TallySubmission r : responses) {
            try {
                cryptoEngineClient.submitChallengeResponse(sid, r.getGuardianId(), r.getPayloadJson());
            } catch (Exception ex) {
                log.warn("replay challenge-response FAIL guardian={}, hata={}", r.getGuardianId(), ex.getMessage());
            }
        }
        log.info("Durable replay tamam: electionId={}, sessionId={}, partials={}, responses={}",
                eid, sid, partials.size(), responses.size());
    }

    // ============ Server-driven (lifecycle hook) ============

    /**
     * Election ACTIVE → CLOSED geçişinde tetiklenir. KMP'de stateful tally
     * session başlatır; encrypted_tally_json ve sessionId'i kalıcılaştırır.
     */
    @Transactional
    public void startSessionForElection(Election election) {
        if (election.getTallySessionId() != null && !election.getTallySessionId().isBlank()) {
            log.info("Distributed tally session zaten başlatılmış: electionId={}, sessionId={}",
                    election.getId(), election.getTallySessionId());
            return;
        }
        if (election.getElectionGuardContext() == null || election.getElectionManifest() == null) {
            log.warn("Distributed session başlatılamadı (context/manifest yok): electionId={}", election.getId());
            return;
        }

        List<String> ciphertextBallots = voteRepository.findByElectionId(election.getId()).stream()
                .map(Vote::getEncryptedBallot)
                .filter(b -> b != null && !b.isBlank())
                .toList();
        if (ciphertextBallots.isEmpty()) {
            log.info("Oy yok, distributed session atlandı: electionId={}", election.getId());
            return;
        }

        // Sprint 5.A leader-mode: participating_guardian_ids JSON kolonundan al
        // (election_guardians tablosu sadece distributed multi-cihaz akışında dolu olur)
        List<String> participatingIds;
        try {
            if (election.getParticipatingGuardianIds() != null
                    && !election.getParticipatingGuardianIds().isBlank()) {
                participatingIds = objectMapper.readValue(
                        election.getParticipatingGuardianIds(),
                        new com.fasterxml.jackson.core.type.TypeReference<List<String>>() {});
            } else {
                // Fallback: election_guardians tablosundan (distributed multi-cihaz akışı)
                List<ElectionGuardian> eligible = guardianRepository.findAllByElectionId(election.getId()).stream()
                        .filter(g -> g.getStatus() == ElectionGuardian.GuardianStatus.KEY_UPLOADED
                                || g.getStatus() == ElectionGuardian.GuardianStatus.SHARE_UPLOADED)
                        .toList();
                participatingIds = eligible.stream().map(g -> g.getUserId().toString()).toList();
            }
        } catch (Exception e) {
            throw new RuntimeException("participating_guardian_ids parse hatası", e);
        }
        if (participatingIds.size() < election.getMinGuardiansThreshold()) {
            log.warn("Yeterli guardian yok (participating={}, quorum={}): electionId={}",
                    participatingIds.size(), election.getMinGuardiansThreshold(), election.getId());
            return;
        }

        StartTallyDecryptionSessionResponse response = cryptoEngineClient.startTallyDecryptionSession(
                String.valueOf(election.getId()),
                election.getElectionGuardContext(),
                election.getElectionManifest(),
                ciphertextBallots,
                participatingIds);

        election.setTallySessionId(response.getSessionId());
        election.setEncryptedTallyJson(response.getEncryptedTallyJson());
        electionRepository.save(election);

        log.info("Distributed tally session başlatıldı: electionId={}, sessionId={}, encryptedTallyJson={} bytes, participating={}",
                election.getId(), response.getSessionId(),
                response.getEncryptedTallyJson().length(), participatingIds.size());
    }

    // ============ Guardian-driven (HTTP endpoints) ============

    /**
     * Guardian (mobile) tally task'ını pull eder: sessionId + encrypted_tally
     * + manifest + jointPublicKey döner. Mobile lokal KMP ile partial
     * decryption hesaplar.
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getDecryptionTask(Long electionId, String userId) {
        Election election = electionRepository.findById(electionId)
                .orElseThrow(() -> ApiException.notFound("Seçim bulunamadı"));
        ElectionGuardian guardian = guardianRepository.findByElectionIdAndUserId(electionId, UUID.fromString(userId))
                .orElse(null);
        if (guardian == null && !election.getCreatedBy().equals(userId)) {
            throw ApiException.forbidden("Bu seçim için emanetçi veya owner değilsiniz");
        }
        if (election.getTallySessionId() == null) {
            throw ApiException.badRequest("Tally session henüz başlatılmadı");
        }
        return Map.of(
                "sessionId", election.getTallySessionId(),
                "guardianId", guardian.getUserId().toString(),
                "encryptedTallyJson", election.getEncryptedTallyJson(),
                "electionGuardContext", election.getElectionGuardContext(),
                "electionManifest", election.getElectionManifest(),
                "jointPublicKey", election.getElectionPublicKey()
        );
    }

    /**
     * Round 1 — mobile partial decryption sonucunu submit eder.
     * @param guardianIdOverride leader-mode'da trustee id (user_id yerine).
     *                            null ise user_id kullanılır (multi-cihaz distributed).
     */
    @Transactional
    public Map<String, Object> submitPartialDecryption(Long electionId, String userId, String partialsJson,
                                                        String guardianIdOverride) {
        Election election = electionRepository.findById(electionId)
                .orElseThrow(() -> ApiException.notFound("Seçim bulunamadı"));
        ElectionGuardian guardian = guardianRepository.findByElectionIdAndUserId(electionId, UUID.fromString(userId))
                .orElse(null);
        if (guardian == null && !election.getCreatedBy().equals(userId)) {
            throw ApiException.forbidden("Bu seçim için emanetçi veya owner değilsiniz");
        }
        if (election.getTallySessionId() == null) {
            throw ApiException.badRequest("Tally session başlatılmadı");
        }

        String effectiveGuardianId = (guardianIdOverride != null && !guardianIdOverride.isBlank())
                ? guardianIdOverride
                : (guardian != null ? guardian.getUserId().toString() : userId);

        SessionAckResponse ack = cryptoEngineClient.submitPartialDecryption(
                election.getTallySessionId(), effectiveGuardianId, partialsJson);

        // Faz 2.6 — crypto-engine restart/crash → session kayıp. Yeni session
        // aç, persisted partial/response'ları replay et, mevcut submit'i tekrarla.
        if (isSessionLost(ack)) {
            restartAndReplay(election);
            Election fresh = electionRepository.findById(electionId).orElseThrow();
            ack = cryptoEngineClient.submitPartialDecryption(
                    fresh.getTallySessionId(), effectiveGuardianId, partialsJson);
        }

        if (ack.getAccepted()) {
            // Durable: bu partial'ı kalıcılaştır (restart sonrası replay için)
            persistSubmission(electionId, effectiveGuardianId,
                    TallySubmission.ROUND_PARTIAL, partialsJson);
            if (guardian != null) {
                guardian.setStatus(ElectionGuardian.GuardianStatus.SHARE_UPLOADED);
                guardianRepository.save(guardian);
            }
        }
        return Map.of("accepted", ack.getAccepted(), "state", ack.getState(), "error", ack.getError());
    }

    /** Round 2 — mobile challenges'ı pull eder (long-poll). */
    public Map<String, Object> getChallenges(Long electionId, String userId, String guardianIdOverride) {
        Election election = electionRepository.findById(electionId)
                .orElseThrow(() -> ApiException.notFound("Seçim bulunamadı"));
        ElectionGuardian guardian = guardianRepository.findByElectionIdAndUserId(electionId, UUID.fromString(userId))
                .orElse(null);
        if (guardian == null && !election.getCreatedBy().equals(userId)) {
            throw ApiException.forbidden("Bu seçim için emanetçi veya owner değilsiniz");
        }
        if (election.getTallySessionId() == null) {
            throw ApiException.badRequest("Tally session başlatılmadı");
        }

        String effectiveGuardianId = (guardianIdOverride != null && !guardianIdOverride.isBlank())
                ? guardianIdOverride
                : (guardian != null ? guardian.getUserId().toString() : userId);

        GetChallengesResponse response = cryptoEngineClient.getChallenges(
                election.getTallySessionId(), effectiveGuardianId);
        return Map.of("challengesJson", response.getChallengesJson());
    }

    /**
     * Round 3 — mobile challenge response submit eder. Quorum tamamlandıysa
     * (server tarafında DecryptorDoerre background thread'i bitince)
     * otomatik finalize'ı tetikler.
     */
    @Transactional
    public Map<String, Object> submitChallengeResponse(Long electionId, String userId, String responsesJson,
                                                       String guardianIdOverride) {
        Election election = electionRepository.findById(electionId)
                .orElseThrow(() -> ApiException.notFound("Seçim bulunamadı"));
        ElectionGuardian guardian = guardianRepository.findByElectionIdAndUserId(electionId, UUID.fromString(userId))
                .orElse(null);
        if (guardian == null && !election.getCreatedBy().equals(userId)) {
            throw ApiException.forbidden("Bu seçim için emanetçi veya owner değilsiniz");
        }
        if (election.getTallySessionId() == null) {
            throw ApiException.badRequest("Tally session başlatılmadı");
        }

        String effectiveGuardianId = (guardianIdOverride != null && !guardianIdOverride.isBlank())
                ? guardianIdOverride
                : (guardian != null ? guardian.getUserId().toString() : userId);

        SessionAckResponse ack = cryptoEngineClient.submitChallengeResponse(
                election.getTallySessionId(), effectiveGuardianId, responsesJson);

        // Faz 2.6 — session kayıpsa: yeni session + replay + tekrar dene.
        if (isSessionLost(ack)) {
            restartAndReplay(election);
            Election fresh = electionRepository.findById(electionId).orElseThrow();
            ack = cryptoEngineClient.submitChallengeResponse(
                    fresh.getTallySessionId(), effectiveGuardianId, responsesJson);
        }

        // Leader-mode tek user N trustee için N kez submit eder; tally finalize
        // tetikleyici sayacı bu durumda anlamsız (count by guardian id). Pratik
        // basitleştirme: submit her zaman başarılı, finalize'ı body request etsin.
        if (ack.getAccepted()) {
            // Durable: challenge response'u kalıcılaştır (restart replay için)
            persistSubmission(electionId, effectiveGuardianId,
                    TallySubmission.ROUND_CHALLENGE_RESPONSE, responsesJson);
            if (guardian != null) {
                guardian.setCoefficientProofs("ROUND_3_DONE");
                guardianRepository.save(guardian);
            }
        }

        long doneCount = guardianRepository.findAllByElectionId(electionId).stream()
                .filter(g -> "ROUND_3_DONE".equals(g.getCoefficientProofs()))
                .count();
        boolean shouldFinalize = doneCount >= election.getMinGuardiansThreshold()
                && (election.getTallyProof() == null || election.getTallyProof().isBlank());

        Map<String, Object> result = new java.util.HashMap<>();
        result.put("accepted", ack.getAccepted());
        result.put("state", ack.getState());
        result.put("error", ack.getError());
        result.put("doneCount", doneCount);
        result.put("quorum", election.getMinGuardiansThreshold());
        result.put("finalized", false);

        if (shouldFinalize) {
            try {
                finalize(election);
                result.put("finalized", true);
            } catch (Exception e) {
                log.error("Distributed tally finalize FAIL: electionId={}", election.getId(), e);
                result.put("finalizeError", e.getMessage());
            }
        }
        return result;
    }

    /**
     * Leader-mode: owner explicit finalize eder (auto-finalize tetiklenmez
     * çünkü election_guardians boş). Idempotent — zaten yapıldıysa no-op.
     */
    @Transactional
    public Map<String, Object> finalizeByOwner(Long electionId, String userId) {
        Election election = electionRepository.findById(electionId)
                .orElseThrow(() -> ApiException.notFound("Seçim bulunamadı"));
        if (!election.getCreatedBy().equals(userId)) {
            throw ApiException.forbidden("Sadece seçim sahibi finalize edebilir");
        }
        if (election.getTallySessionId() == null) {
            throw ApiException.badRequest("Tally session yok");
        }
        if (election.getTallyProof() != null && !election.getTallyProof().isBlank()) {
            return Map.of("alreadyFinalized", true, "tallyResults", election.getTallyResults());
        }
        finalize(election);
        return Map.of("alreadyFinalized", false, "tallyResults", election.getTallyResults());
    }

    /** Quorum sonrası tally finalize — KMP DecryptorDoerre tamamladı. */
    @Transactional
    public void finalize(Election election) {
        log.info("Distributed tally finalize başlıyor: electionId={}, sessionId={}",
                election.getId(), election.getTallySessionId());

        TallyElectionResponse response = cryptoEngineClient.finalizeTallyDecryption(election.getTallySessionId());
        election.setTallyProof(response.getTallyProof());

        List<Map<String, Object>> contests = response.getResultsList().stream()
                .map(contest -> Map.<String, Object>of(
                        "contest_id", contest.getContestId(),
                        "selections", contest.getSelectionsList().stream()
                                .map(sel -> Map.<String, Object>of(
                                        "selection_id", sel.getSelectionId(),
                                        "tally", sel.getTally()))
                                .collect(Collectors.toList())))
                .toList();
        try {
            election.setTallyResults(objectMapper.writeValueAsString(contests));
        } catch (Exception e) {
            throw new RuntimeException("Tally results JSON serialize hatası", e);
        }
        electionRepository.save(election);

        // Faz 1.1 — TALLY_PUBLISHED kritik şeffaflık kaydı: tally sonucu ile
        // AYNI transaction'da outbox'a yazılır (atomik), publisher sıralı +
        // retry'lı + idempotent teslim eder. Artık sessizce kaybolmaz.
        bulletinOutbox.enqueue(
                String.valueOf(election.getId()),
                "TALLY_PUBLISHED",
                null, null,
                election.getTallyResults(),
                true);

        log.info("Distributed tally publish OK: electionId={}, contests={}",
                election.getId(), contests.size());
    }
}
