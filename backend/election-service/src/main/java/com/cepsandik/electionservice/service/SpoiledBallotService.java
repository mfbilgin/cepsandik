package com.cepsandik.electionservice.service;

import com.cepsandik.electionservice.client.CryptoEngineClient;
import com.cepsandik.electionservice.dto.request.SpoilBallotRequest;
import com.cepsandik.electionservice.dto.response.SpoilBallotResponse;
import com.cepsandik.electionservice.entity.Election;
import com.cepsandik.electionservice.entity.SpoiledBallot;
import com.cepsandik.electionservice.exception.ApiException;
import com.cepsandik.electionservice.grpc.VerifySpoiledBallotResponse;
import com.cepsandik.electionservice.repository.ElectionRepository;
import com.cepsandik.electionservice.repository.SpoiledBallotRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Faz 1.4 — Benaloh challenge orchestrasyonu (cast-as-intended).
 *
 * Akış: cihaz bir ballot şifreler ama CAST ETMEZ; "spoil" eder ve primary
 * nonce'ı açar. Burada crypto-engine'e gönderilir, DecryptWithNonce ile
 * BAĞIMSIZ (trustless) çözülür. Çözülen seçim cihazın seçmene gösterdiğiyle
 * eşleşirse cihaz dürüst şifrelemiştir. Spoiled ballot votes tablosuna
 * YAZILMAZ → tally'ye asla girmez. Şeffaflık için BALLOT_SPOILED bulletin
 * kaydı (outbox). Aynı ciphertext bir daha cast edilemez (gizlilik).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SpoiledBallotService {

    private final ElectionRepository electionRepository;
    private final SpoiledBallotRepository spoiledBallotRepository;
    private final CryptoEngineClient cryptoEngineClient;
    private final BulletinOutboxService bulletinOutbox;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Transactional
    public SpoilBallotResponse spoilBallot(Long electionId, SpoilBallotRequest request) {
        Election election = electionRepository.findById(electionId)
                .orElseThrow(() -> ApiException.notFound("Seçim bulunamadı: " + electionId));
        if (election.getElectionGuardContext() == null || election.getElectionManifest() == null) {
            throw ApiException.badRequest("Seçim kriptografik bağlamı hazır değil (ceremony tamamlanmadı)");
        }

        // Trustless doğrulama — crypto-engine ciphertext'i SADECE nonce + joint
        // key ile çözer; cihaza güvenmez.
        VerifySpoiledBallotResponse verify = cryptoEngineClient.verifySpoiledBallot(
                String.valueOf(electionId),
                request.getEncryptedBallot(),
                request.getPrimaryNonce(),
                election.getElectionGuardContext(),
                election.getElectionManifest(),
                request.getPlaintextBallot());

        List<Map<String, Object>> selections = new ArrayList<>();
        verify.getSelectionsList().forEach(s -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("selectionId", s.getSelectionId());
            m.put("vote", s.getVote());
            selections.add(m);
        });

        // Spoiled ballot kalıcı kayıt — aynı ciphertext bir daha cast edilemez.
        spoiledBallotRepository.save(SpoiledBallot.builder()
                .electionId(electionId)
                .ballotId(request.getBallotId())
                .trackingCode(request.getTrackingCode())
                .ballotHash(request.getBallotHash())
                .verified(verify.getVerified())
                .build());

        // Şeffaflık: BALLOT_SPOILED bulletin kaydı (auditable, vote-integrity
        // kritik değil — spoiled ballot zaten sayılmaz). Payload: nonce ile
        // çözülen plaintext + verified.
        String payload;
        try {
            Map<String, Object> rec = new LinkedHashMap<>();
            rec.put("ballotId", request.getBallotId());
            rec.put("trackingCode", request.getTrackingCode());
            rec.put("verified", verify.getVerified());
            rec.put("decryptedSelections", selections);
            payload = objectMapper.writeValueAsString(rec);
        } catch (Exception e) {
            payload = "{\"ballotId\":\"" + request.getBallotId() + "\",\"verified\":"
                    + verify.getVerified() + "}";
        }
        bulletinOutbox.enqueue(String.valueOf(electionId), "BALLOT_SPOILED",
                request.getTrackingCode(), request.getBallotHash(), payload, false);

        if (!verify.getVerified()) {
            log.warn("Spoiled ballot DOĞRULANAMADI: election={}, ballot={}, sebep={}",
                    electionId, request.getBallotId(), verify.getError());
            return SpoilBallotResponse.builder()
                    .verified(false)
                    .ballotId(request.getBallotId())
                    .trackingCode(request.getTrackingCode())
                    .decryptedSelections(selections)
                    .message("Cihaz dürüst şifrelememiş olabilir: " + verify.getError())
                    .build();
        }

        log.info("Spoiled ballot doğrulandı (cast-as-intended): election={}, ballot={}",
                electionId, request.getBallotId());
        return SpoilBallotResponse.builder()
                .verified(true)
                .ballotId(request.getBallotId())
                .trackingCode(request.getTrackingCode())
                .decryptedSelections(selections)
                .message("Şifreleme dürüst: cihaz tam olarak seçtiğiniz seçenekleri "
                        + "şifrelemiş. Bu ballot SAYILMAYACAK — gerçek oyunuz için "
                        + "yeni bir ballot şifreleyip kullanın.")
                .build();
    }
}
