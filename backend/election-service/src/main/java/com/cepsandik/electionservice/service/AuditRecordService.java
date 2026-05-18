package com.cepsandik.electionservice.service;

import com.cepsandik.electionservice.client.BulletinBoardClient;
import com.cepsandik.electionservice.entity.Election;
import com.cepsandik.electionservice.entity.Vote;
import com.cepsandik.electionservice.exception.ApiException;
import com.cepsandik.electionservice.repository.ElectionRepository;
import com.cepsandik.electionservice.repository.VoteRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Faz 1.3 — Bağımsız denetlenebilir tam election record.
 *
 * Üçüncü bir tarafın (gözlemci/denetçi) standart ElectionGuard verifier ile
 * seçimi doğrulaması için gereken HER ŞEYİ tek JSON'da toplar:
 *  - manifest + electionguard context + joint public key (N, Q)
 *  - tüm şifreli oylar (tracking code, ballot hash, ciphertext, ZKP)
 *  - şifreli olmayan tally sonucu + GERÇEK tally proof (Chaum-Pedersen)
 *  - bulletin board hash-zinciri + zincir bütünlük kontrolü (sunucu tarafı
 *    yeniden hesap; denetçi bağımsız tekrar doğrulayabilir)
 *
 * Public (kimlik doğrulaması gerektirmez) — şeffaflık verisi zaten herkese
 * açıktır; gizli hiçbir şey içermez (oylar şifreli, key share'ler asla burada
 * değil).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AuditRecordService {

    private final ElectionRepository electionRepository;
    private final VoteRepository voteRepository;
    private final BulletinBoardClient bulletinBoardClient;

    @Transactional(readOnly = true)
    public Map<String, Object> buildAuditRecord(Long electionId) {
        Election e = electionRepository.findById(electionId)
                .orElseThrow(() -> ApiException.notFound("Seçim bulunamadı: " + electionId));

        Map<String, Object> record = new LinkedHashMap<>();
        record.put("electionId", electionId);
        record.put("title", e.getTitle());
        record.put("status", e.getStatus() != null ? e.getStatus().name() : null);
        record.put("quorum", e.getMinGuardiansThreshold());
        // participatingGuardianIds JSON dizisi (["trustee1",...]) — N bundan türetilir.
        record.put("participatingGuardianIds", e.getParticipatingGuardianIds());

        // Kriptografik bağlam (EG verifier girişi)
        Map<String, Object> crypto = new LinkedHashMap<>();
        crypto.put("electionManifest", e.getElectionManifest());
        crypto.put("electionGuardContext", e.getElectionGuardContext());
        crypto.put("jointPublicKey", e.getElectionPublicKey());
        record.put("cryptoContext", crypto);

        // Tüm şifreli oylar (recorded-as-cast doğrulaması)
        List<Vote> votes = voteRepository.findByElectionId(electionId);
        List<Map<String, Object>> ballots = new ArrayList<>();
        for (Vote v : votes) {
            Map<String, Object> b = new LinkedHashMap<>();
            b.put("ballotId", v.getBallotId());
            b.put("trackingCode", v.getTrackingCode());
            b.put("ballotHash", v.getBallotHash());
            b.put("encryptedBallot", v.getEncryptedBallot());
            b.put("zkpProof", v.getZkpProof());
            b.put("castAt", v.getCastAt() != null ? v.getCastAt().toString() : null);
            ballots.add(b);
        }
        record.put("encryptedBallotCount", ballots.size());
        record.put("encryptedBallots", ballots);

        // Tally + GERÇEK proof (Chaum-Pedersen decryption proof'ları)
        Map<String, Object> tally = new LinkedHashMap<>();
        tally.put("tallyResults", e.getTallyResults());
        tally.put("tallyProof", e.getTallyProof());
        record.put("tally", tally);

        // Bulletin board hash-zinciri + bütünlük kontrolü
        Map<String, Object> bulletin = new LinkedHashMap<>();
        try {
            List<Map<String, Object>> chain =
                    bulletinBoardClient.fetchElectionRecord(String.valueOf(electionId));
            bulletin.put("records", chain);
            bulletin.put("recordCount", chain.size());
            bulletin.put("chainIntegrity", verifyChain(String.valueOf(electionId), chain));
        } catch (Exception ex) {
            log.warn("Audit record: bulletin zinciri çekilemedi election={}, hata={}",
                    electionId, ex.getMessage());
            bulletin.put("error", "Bulletin board erişilemedi: " + ex.getMessage());
        }
        record.put("bulletinBoard", bulletin);

        record.put("verificationGuide",
                "Doğrulama: (1) electionManifest+electionGuardContext+jointPublicKey "
                + "ile EG bağlamını kurun. (2) Her encryptedBallot'un zkpProof'unu "
                + "doğrulayın (cast-as-intended/well-formedness). (3) tallyProof "
                + "(DecryptedTallyOrBallot) Chaum-Pedersen proof'larını jointPublicKey'e "
                + "karşı doğrulayın. (4) bulletinBoard.chainIntegrity.valid=true ve "
                + "kayıtların recordHash zincirini bağımsız yeniden hesaplayın. "
                + "Referans: votingworks/electionguard-kotlin-multiplatform verifier.");
        return record;
    }

    /**
     * Bulletin hash-zinciri bütünlüğünü sunucu tarafında yeniden hesaplayıp
     * doğrular. Denetçi bunu bağımsız olarak da tekrar yapmalıdır (bu sadece
     * kolaylık + erken tespit; güven kaynağı değil).
     */
    private Map<String, Object> verifyChain(String electionId, List<Map<String, Object>> chain) {
        Map<String, Object> result = new LinkedHashMap<>();
        String prevHash = null;
        int idx = 0;
        for (Map<String, Object> rec : chain) {
            String recordType = str(rec.get("recordType"));
            String trackingCode = str(rec.get("trackingCode"));
            String ballotHash = str(rec.get("ballotHash"));
            String payload = str(rec.get("payload"));
            String expected = sha256Hex(String.join("|",
                    electionId,
                    recordType,
                    nullToEmpty(trackingCode),
                    nullToEmpty(ballotHash),
                    nullToEmpty(prevHash),
                    nullToEmpty(payload)));
            String actual = str(rec.get("recordHash"));
            String linkedPrev = str(rec.get("previousHash"));
            boolean hashOk = expected.equals(actual);
            boolean linkOk = (prevHash == null && (linkedPrev == null || linkedPrev.isEmpty()))
                    || (prevHash != null && prevHash.equals(linkedPrev));
            if (!hashOk || !linkOk) {
                result.put("valid", false);
                result.put("brokenAtIndex", idx);
                result.put("brokenRecordType", recordType);
                result.put("reason", !hashOk
                        ? "recordHash beklenenle uyuşmuyor (kurcalama?)"
                        : "previousHash zincir bağlantısı kopuk");
                return result;
            }
            prevHash = actual;
            idx++;
        }
        result.put("valid", true);
        result.put("verifiedRecords", idx);
        return result;
    }

    private static String str(Object o) {
        return o == null ? null : String.valueOf(o);
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }

    private static String sha256Hex(String value) {
        try {
            byte[] d = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(d);
        } catch (Exception ex) {
            throw new IllegalStateException("SHA-256 yok", ex);
        }
    }
}
