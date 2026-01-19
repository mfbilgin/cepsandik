package com.cepsandik.electionservice.repository;

import com.cepsandik.electionservice.entity.Candidate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CandidateRepository extends JpaRepository<Candidate, Long> {

    List<Candidate> findByElectionIdAndIsDeletedFalseOrderByDisplayOrderAsc(Long electionId);

    Optional<Candidate> findByIdAndElectionIdAndIsDeletedFalse(Long id, Long electionId);

    long countByElectionIdAndIsDeletedFalse(Long electionId);

    boolean existsByElectionIdAndNameAndIsDeletedFalse(Long electionId, String name);
}
