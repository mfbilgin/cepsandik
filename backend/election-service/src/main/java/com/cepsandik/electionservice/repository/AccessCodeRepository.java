package com.cepsandik.electionservice.repository;

import com.cepsandik.electionservice.entity.AccessCode;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AccessCodeRepository extends JpaRepository<AccessCode, Long> {

    Optional<AccessCode> findByCode(String code);

    Optional<AccessCode> findByCodeAndIsActiveTrue(String code);

    List<AccessCode> findByElectionIdAndIsActiveTrue(Long electionId);

    boolean existsByCode(String code);

    long countByElectionIdAndIsActiveTrue(Long electionId);
}
