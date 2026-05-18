package com.cepsandik.electionservice.service;

import com.cepsandik.electionservice.client.BulletinBoardClient;
import com.cepsandik.electionservice.entity.BulletinOutbox;
import com.cepsandik.electionservice.repository.BulletinOutboxRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Faz 1.1 — Transactional outbox publisher.
 *
 * Yayımlanmamış {@link BulletinOutbox} satırlarını id sırasıyla bulletin-board
 * servisine teslim eder. Hash-zinciri bütünlüğü için bir election'ın kayıtları
 * KESİNLİKLE sıra ile yayımlanır: bir kayıt başarısız olursa o election'ın
 * sonraki kayıtları bu turda atlanır (bir sonraki turda baştan denenir).
 * idempotencyKey sayesinde retry'da BB'de çift zincir kaydı oluşmaz.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class BulletinOutboxPublisher {

    private static final int BATCH = 200;

    private final BulletinOutboxRepository repository;
    private final BulletinBoardClient bulletinBoardClient;

    @Scheduled(fixedDelayString = "${app.bulletin-outbox.publish-interval-ms:5000}")
    @Transactional
    public void publishPending() {
        List<BulletinOutbox> pending = repository.findPendingOrdered(PageRequest.of(0, BATCH));
        if (pending.isEmpty()) {
            return;
        }

        // Election bazında sıra koru: bir kayıt fail olursa o election'ın
        // sonraki kayıtlarını bu turda atla (zincir bozulmasın).
        Set<String> blockedElections = new HashSet<>();
        int ok = 0, fail = 0;

        for (BulletinOutbox row : pending) {
            if (blockedElections.contains(row.getElectionId())) {
                continue;
            }
            try {
                bulletinBoardClient.appendRecord(
                        row.getElectionId(),
                        row.getRecordType(),
                        row.getTrackingCode(),
                        row.getBallotHash(),
                        row.getPayload(),
                        row.getIdempotencyKey().toString());
                row.setPublished(true);
                row.setPublishedAt(Instant.now());
                row.setLastError(null);
                row.setAttempts(row.getAttempts() + 1);
                repository.save(row);
                ok++;
            } catch (Exception e) {
                row.setAttempts(row.getAttempts() + 1);
                row.setLastError(truncate(e.getMessage()));
                repository.save(row);
                blockedElections.add(row.getElectionId());
                fail++;
                log.warn("Bulletin outbox publish FAIL (election={} bu turda durduruldu): "
                                + "type={}, attempt={}, hata={}",
                        row.getElectionId(), row.getRecordType(), row.getAttempts(),
                        e.getMessage());
            }
        }
        if (ok > 0 || fail > 0) {
            log.info("Bulletin outbox publish turu: ok={}, fail={}, blockedElections={}",
                    ok, fail, blockedElections.size());
        }
    }

    private static String truncate(String s) {
        if (s == null) return null;
        return s.length() > 1000 ? s.substring(0, 1000) : s;
    }
}
