package com.cepsandik.electionservice.repository;

import com.cepsandik.electionservice.entity.VoteNullifier;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface VoteNullifierRepository extends JpaRepository<VoteNullifier, Long> {

    boolean existsByElectionIdAndNullifierHash(Long electionId, String nullifierHash);

    Optional<VoteNullifier> findByElectionIdAndNullifierHash(Long electionId, String nullifierHash);
}
