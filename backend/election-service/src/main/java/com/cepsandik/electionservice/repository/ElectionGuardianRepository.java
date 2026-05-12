package com.cepsandik.electionservice.repository;

import com.cepsandik.electionservice.entity.Election;
import com.cepsandik.electionservice.entity.ElectionGuardian;
import org.springframework.data.jpa.repository.JpaRepository;
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
}
