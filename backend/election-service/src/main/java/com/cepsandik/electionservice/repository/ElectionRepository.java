package com.cepsandik.electionservice.repository;

import com.cepsandik.electionservice.entity.Election;
import com.cepsandik.electionservice.enums.ElectionStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface ElectionRepository extends JpaRepository<Election, Long> {

    Optional<Election> findByIdAndIsDeletedFalse(Long id);

    Page<Election> findByCommunityIdAndIsDeletedFalse(Long communityId, Pageable pageable);

    Page<Election> findByCommunityIdAndStatusAndIsDeletedFalse(
            Long communityId, ElectionStatus status, Pageable pageable);

    List<Election> findByCreatedByAndIsDeletedFalse(String userId);

    /** Başlaması gereken seçimleri bul (SCHEDULED → ACTIVE) */
    @Query("SELECT e FROM Election e WHERE e.status = 'SCHEDULED' " +
           "AND e.startTime <= :now AND e.isDeleted = false")
    List<Election> findElectionsToStart(@Param("now") LocalDateTime now);

    /** Bitmesi gereken seçimleri bul (ACTIVE → CLOSED) */
    @Query("SELECT e FROM Election e WHERE e.status = 'ACTIVE' " +
           "AND e.endTime <= :now AND e.isDeleted = false")
    List<Election> findElectionsToEnd(@Param("now") LocalDateTime now);

    /** Topluluk bazlı aktif seçimleri getir */
    @Query("SELECT e FROM Election e WHERE e.communityId = :communityId " +
           "AND e.status = 'ACTIVE' AND e.isDeleted = false")
    List<Election> findActiveElectionsByCommunityId(@Param("communityId") Long communityId);

    /** Kullanıcının oluşturduğu seçim sayısı */
    long countByCreatedByAndIsDeletedFalse(String userId);
}
