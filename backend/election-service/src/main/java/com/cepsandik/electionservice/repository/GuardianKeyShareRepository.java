package com.cepsandik.electionservice.repository;

import com.cepsandik.electionservice.entity.GuardianKeyShare;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface GuardianKeyShareRepository extends JpaRepository<GuardianKeyShare, Long> {

    List<GuardianKeyShare> findByElectionIdAndToGuardianId(Long electionId, String toGuardianId);

    List<GuardianKeyShare> findByElectionId(Long electionId);

    boolean existsByElectionIdAndFromGuardianIdAndToGuardianId(
            Long electionId, String fromGuardianId, String toGuardianId);

    void deleteByElectionId(Long electionId);
}
