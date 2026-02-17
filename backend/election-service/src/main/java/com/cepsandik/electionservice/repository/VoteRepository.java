package com.cepsandik.electionservice.repository;

import com.cepsandik.electionservice.entity.Vote;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface VoteRepository extends JpaRepository<Vote, Long> {

    /** Token ile oy bul (idempotency kontrolü) */
    Optional<Vote> findByVoteToken(String voteToken);

    /** Token ile oy mevcut mu */
    boolean existsByVoteToken(String voteToken);

    /** Seçimdeki toplam oy sayısı */
    long countByElectionId(Long electionId);

    /** Aday başına oy sayısı */
    long countByElectionIdAndCandidateId(Long electionId, Long candidateId);

    /** Seçimdeki tüm oylar */
    List<Vote> findByElectionId(Long electionId);

    /** Aday bazlı oy sayıları (sonuç hesaplama için) */
    @Query("SELECT v.candidate.id, COUNT(v) FROM Vote v WHERE v.election.id = :electionId GROUP BY v.candidate.id ORDER BY COUNT(v) DESC")
    List<Object[]> countVotesByCandidateForElection(@Param("electionId") Long electionId);
}
