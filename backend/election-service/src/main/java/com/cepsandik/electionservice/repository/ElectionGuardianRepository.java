package com.cepsandik.electionservice.repository;

import com.cepsandik.electionservice.entity.Election;
import com.cepsandik.electionservice.entity.ElectionGuardian;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ElectionGuardianRepository extends JpaRepository<ElectionGuardian, Long> {
    List<ElectionGuardian> findAllByElection(Election election);
    List<ElectionGuardian> findAllByElectionId(Long electionId);
    List<ElectionGuardian> findByUserId(UUID userId);
    Optional<ElectionGuardian> findByElectionIdAndUserId(Long electionId, UUID userId);
    long countByElectionIdAndStatus(Long electionId, ElectionGuardian.GuardianStatus status);
    List<ElectionGuardian> findByElectionIdAndStatus(Long electionId, ElectionGuardian.GuardianStatus status);

    // xCoordinate / isBackup alanları JavaBeans introspector tuzağına
    // (tek küçük harf + büyük harf → getXCoordinate/isBackup) düşüyor;
    // derived query parser çözemiyor. Açık JPQL ile güvenli.
    @Query("SELECT g FROM ElectionGuardian g WHERE g.election.id = :electionId ORDER BY g.xCoordinate ASC")
    List<ElectionGuardian> findAllByElectionIdOrderByXCoordinateAsc(@Param("electionId") Long electionId);

    @Query("SELECT g FROM ElectionGuardian g WHERE g.election.id = :electionId AND g.isBackup = true ORDER BY g.backupOrder ASC")
    List<ElectionGuardian> findBackupsByElectionId(@Param("electionId") Long electionId);
}
