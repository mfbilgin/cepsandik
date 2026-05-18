package com.cepsandik.bulletinboard.repository;

import com.cepsandik.bulletinboard.entity.BulletinRecord;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface BulletinRecordRepository extends MongoRepository<BulletinRecord, String> {
    List<BulletinRecord> findByElectionIdOrderByIdAsc(String electionId);
    Optional<BulletinRecord> findTopByElectionIdOrderByIdDesc(String electionId);
    Optional<BulletinRecord> findByElectionIdAndTrackingCode(String electionId, String trackingCode);
    Optional<BulletinRecord> findByIdempotencyKey(String idempotencyKey);
}
