package com.cepsandik.electionservice.repository;

import com.cepsandik.electionservice.entity.Election;
import com.cepsandik.electionservice.enums.ElectionStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface ElectionRepository extends JpaRepository<Election, Long> {

       Optional<Election> findByIdAndIsDeletedFalse(Long id);

       @Query("SELECT e FROM Election e WHERE e.communityId = :communityId " +
                     "AND e.isDeleted = false " +
                     "AND (e.endTime IS NULL OR e.endTime > :now OR e.status IN ('CLOSED', 'ARCHIVED'))")
       Page<Election> findByCommunityIdAndIsDeletedActive(@Param("communityId") Long communityId,
                     @Param("now") Instant now, Pageable pageable);

       Page<Election> findByCommunityIdAndIsDeletedFalse(Long communityId, Pageable pageable);

       Page<Election> findByCommunityIdAndStatusInAndIsDeletedFalse(
                     Long communityId, java.util.List<ElectionStatus> statuses, Pageable pageable);

       Page<Election> findByCommunityIdAndStatusAndIsDeletedFalse(
                     Long communityId, ElectionStatus status, Pageable pageable);

       List<Election> findByCreatedByAndIsDeletedFalse(String userId);

       /** Başlaması gereken seçimleri bul (SCHEDULED → ACTIVE) */
       @Query("SELECT e FROM Election e WHERE e.status = :scheduled " +
                     "AND e.startTime <= :now AND e.isDeleted = false")
       List<Election> findElectionsToStart(@Param("scheduled") ElectionStatus scheduled,
                     @Param("now") Instant now);

       /**
        * Bitmesi gereken seçimleri bul (ACTIVE → CLOSED).
        * `tallyProof IS NULL` filter'ı tally idempotency için: aynı seçim
        * için tally bir kere yazıldıktan sonra bu sorgu onu döndürmez.
        */
       @Query("SELECT e FROM Election e WHERE e.status = :active " +
                     "AND e.endTime <= :now AND e.isDeleted = false " +
                     "AND e.tallyProof IS NULL")
       List<Election> findElectionsToEnd(@Param("active") ElectionStatus active,
                     @Param("now") Instant now);

       /**
        * Faz 2.8 — Süresi geçmiş, joint key'i HÂLÂ üretilmemiş ceremony'ler
        * (SCHEDULED + setupDeadline geçti + electionPublicKey yok). Süresiz
        * asılı kalmasın diye scheduler bunları CANCELLED'a alır.
        */
       @Query("SELECT e FROM Election e WHERE e.status = :scheduled " +
                     "AND e.setupDeadline IS NOT NULL AND e.setupDeadline < :now " +
                     "AND (e.electionPublicKey IS NULL OR e.electionPublicKey = '') " +
                     "AND e.isDeleted = false")
       List<Election> findOverdueCeremonies(@Param("scheduled") ElectionStatus scheduled,
                     @Param("now") Instant now);

       /**
        * Faz 2.8 — Takılı tally'ler: CLOSED + tally session var ama tally_proof
        * yok + endTime üstünden grace süresi geçti. Scheduler durable-replay
        * ile tekrar dener; cap aşılınca TALLY_FAILED.
        */
       @Query("SELECT e FROM Election e WHERE e.status = :closed " +
                     "AND e.tallySessionId IS NOT NULL " +
                     "AND (e.tallyProof IS NULL OR e.tallyProof = '') " +
                     "AND e.endTime < :graceBefore AND e.isDeleted = false")
       List<Election> findStuckTallies(@Param("closed") ElectionStatus closed,
                     @Param("graceBefore") Instant graceBefore);

       /** Topluluk bazlı aktif seçimleri getir */
       @Query("SELECT e FROM Election e WHERE e.communityId = :communityId " +
                     "AND e.status = 'ACTIVE' AND e.isDeleted = false")
       List<Election> findActiveElectionsByCommunityId(@Param("communityId") Long communityId);

       /** Kullanıcının oluşturduğu seçim sayısı */
       long countByCreatedByAndIsDeletedFalse(String userId);

       /** Kullanıcının oluşturduğu aktif/scheduled seçimler */
       @Query("SELECT e FROM Election e WHERE e.createdBy = :userId " +
                     "AND e.status IN ('ACTIVE', 'SCHEDULED') AND e.isDeleted = false " +
                     "AND (e.endTime IS NULL OR e.endTime > :now) " +
                     "ORDER BY e.startTime ASC")
       List<Election> findActiveOrScheduledByCreatedBy(@Param("userId") String userId, @Param("now") Instant now);

       /** Kullanıcının oluşturduğu kapanmış seçimler */
       @Query("SELECT e FROM Election e WHERE e.createdBy = :userId " +
                     "AND e.status IN ('CLOSED', 'ARCHIVED') AND e.isDeleted = false " +
                     "ORDER BY e.endTime DESC")
       List<Election> findClosedByCreatedBy(@Param("userId") String userId);

       /** Topluluk bazlı arşivlenmiş seçimler (CLOSED + ARCHIVED) */
       @Query("SELECT e FROM Election e WHERE e.communityId = :communityId " +
                     "AND e.status IN ('CLOSED', 'ARCHIVED') AND e.isDeleted = false")
       Page<Election> findArchivedByCommunityId(@Param("communityId") Long communityId, Pageable pageable);

       /** Bitiş zamanı yaklaşan aktif seçimler (hatırlatma bildirimi için) */
       @Query("SELECT e FROM Election e WHERE e.status = 'ACTIVE' " +
                     "AND e.endTime > :now AND e.endTime <= :threshold AND e.isDeleted = false")
       List<Election> findElectionsNearingEnd(@Param("now") Instant now,
                     @Param("threshold") Instant threshold);
}
