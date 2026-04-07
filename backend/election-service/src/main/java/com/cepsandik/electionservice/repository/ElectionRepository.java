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

       Page<Election> findByCommunityIdAndStatusAndIsDeletedFalse(
                     Long communityId, ElectionStatus status, Pageable pageable);

       List<Election> findByCreatedByAndIsDeletedFalse(String userId);

       /** Başlaması gereken seçimleri bul (SCHEDULED → ACTIVE) */
       @Query("SELECT e FROM Election e WHERE e.status = :scheduled " +
                     "AND e.startTime <= :now AND e.isDeleted = false")
       List<Election> findElectionsToStart(@Param("scheduled") ElectionStatus scheduled,
                     @Param("now") Instant now);

       /** Bitmesi gereken seçimleri bul (ACTIVE → CLOSED) */
       @Query("SELECT e FROM Election e WHERE e.status = :active " +
                     "AND e.endTime <= :now AND e.isDeleted = false")
       List<Election> findElectionsToEnd(@Param("active") ElectionStatus active,
                     @Param("now") Instant now);

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
