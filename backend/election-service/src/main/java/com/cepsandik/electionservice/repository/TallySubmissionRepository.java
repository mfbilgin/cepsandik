package com.cepsandik.electionservice.repository;

import com.cepsandik.electionservice.entity.TallySubmission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TallySubmissionRepository extends JpaRepository<TallySubmission, Long> {

    Optional<TallySubmission> findByElectionIdAndGuardianIdAndRound(
            Long electionId, String guardianId, String round);

    /** Replay sırası: round'a göre id ASC (gönderim sırası korunur). */
    List<TallySubmission> findByElectionIdAndRoundOrderByIdAsc(Long electionId, String round);

    /**
     * Faz 4.15b — Stuck-tally ayrımı: 0 ise hiç guardian partial göndermemiş
     * → tally "takılı" değil, guardian bekliyor (durable-replay anlamsız).
     */
    long countByElectionId(Long electionId);
}
