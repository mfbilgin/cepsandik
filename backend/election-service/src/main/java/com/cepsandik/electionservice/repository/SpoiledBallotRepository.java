package com.cepsandik.electionservice.repository;

import com.cepsandik.electionservice.entity.SpoiledBallot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SpoiledBallotRepository extends JpaRepository<SpoiledBallot, Long> {

    boolean existsByElectionIdAndBallotHash(Long electionId, String ballotHash);

    boolean existsByElectionIdAndTrackingCode(Long electionId, String trackingCode);

    List<SpoiledBallot> findByElectionId(Long electionId);
}
