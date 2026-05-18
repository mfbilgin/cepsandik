package com.cepsandik.bulletinboard.service;

import com.cepsandik.bulletinboard.dto.AppendRecordRequest;
import com.cepsandik.bulletinboard.entity.BulletinRecord;
import com.cepsandik.bulletinboard.repository.BulletinRecordRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class BulletinBoardService {

    private final BulletinRecordRepository repository;

    public BulletinRecord append(AppendRecordRequest request) {
        // Idempotency: aynı anahtarla daha önce yazıldıysa mevcut kaydı dön
        // (publisher retry'da hash-zinciri çift kayıt oluşturmaz).
        String idemKey = request.getIdempotencyKey();
        if (idemKey != null && !idemKey.isBlank()) {
            Optional<BulletinRecord> existing = repository.findByIdempotencyKey(idemKey);
            if (existing.isPresent()) {
                return existing.get();
            }
        }

        String previousHash = repository.findTopByElectionIdOrderByIdDesc(request.getElectionId())
                .map(BulletinRecord::getRecordHash)
                .orElse(null);
        String recordHash = sha256Hex(String.join("|",
                request.getElectionId(),
                request.getRecordType(),
                nullToEmpty(request.getTrackingCode()),
                nullToEmpty(request.getBallotHash()),
                nullToEmpty(previousHash),
                nullToEmpty(request.getPayload())));

        return repository.save(BulletinRecord.builder()
                .electionId(request.getElectionId())
                .recordType(request.getRecordType())
                .trackingCode(request.getTrackingCode())
                .ballotHash(request.getBallotHash())
                .payload(nullToEmpty(request.getPayload()))
                .previousHash(previousHash)
                .recordHash(recordHash)
                .idempotencyKey(idemKey != null && !idemKey.isBlank() ? idemKey : null)
                .build());
    }

    public List<BulletinRecord> electionRecord(String electionId) {
        return repository.findByElectionIdOrderByIdAsc(electionId);
    }

    public BulletinRecord ballotByTrackingCode(String electionId, String trackingCode) {
        return repository.findByElectionIdAndTrackingCode(electionId, trackingCode)
                .orElseThrow(() -> new IllegalArgumentException("Tracking code not found"));
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private static String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
