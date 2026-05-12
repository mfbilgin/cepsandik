package com.cepsandik.electionservice.repository;

import com.cepsandik.electionservice.entity.Vote;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface VoteRepository extends JpaRepository<Vote, Long> {

    Optional<Vote> findByElectionIdAndBallotId(Long electionId, String ballotId);

    Optional<Vote> findByElectionIdAndTrackingCode(Long electionId, String trackingCode);

    boolean existsByElectionIdAndBallotId(Long electionId, String ballotId);

    long countByElectionId(Long electionId);

    List<Vote> findByElectionId(Long electionId);
}
