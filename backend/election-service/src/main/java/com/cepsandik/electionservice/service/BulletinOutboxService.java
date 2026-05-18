package com.cepsandik.electionservice.service;

import com.cepsandik.electionservice.entity.BulletinOutbox;
import com.cepsandik.electionservice.repository.BulletinOutboxRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Faz 1.1 — Şeffaflık kaydını transactional outbox'a yazar.
 *
 * {@link #enqueue} çağıranın @Transactional'ı içinde çalışır: outbox satırı
 * ceremony/tally state ile AYNI commit'te kalıcı olur. Hiçbir dış HTTP çağrısı
 * burada yapılmaz → ne sessiz kayıp ne rollback riski. Teslimi
 * {@code BulletinOutboxPublisher} sırayla + retry'lı yapar.
 *
 * Eski {@code safeBulletin} (hatayı yutan) bu mekanizmayla değiştirildi.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class BulletinOutboxService {

    private final BulletinOutboxRepository repository;

    /**
     * Şeffaflık kaydını kuyruğa ekler. Çağıranın transaction'ına katılır.
     *
     * @param critical true ise verifiability için zorunlu kayıt — yayımlanmadan
     *                 election arşivlenemez / sonuç publish edilemez.
     */
    public void enqueue(String electionId, String recordType, String trackingCode,
                        String ballotHash, String payload, boolean critical) {
        BulletinOutbox row = BulletinOutbox.builder()
                .idempotencyKey(UUID.randomUUID())
                .electionId(electionId)
                .recordType(recordType)
                .trackingCode(trackingCode)
                .ballotHash(ballotHash)
                .payload(payload)
                .critical(critical)
                .published(false)
                .attempts(0)
                .build();
        repository.save(row);
        log.info("Bulletin outbox enqueued: election={}, type={}, critical={}, key={}",
                electionId, recordType, critical, row.getIdempotencyKey());
    }

    /** Yayımlanmamış kritik şeffaflık kaydı var mı (arşiv/sonuç gate'i). */
    public long unpublishedCriticalCount(String electionId) {
        return repository.countUnpublishedCritical(electionId);
    }
}
