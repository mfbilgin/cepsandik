package com.cepsandik.electionservice.repository;

import com.cepsandik.electionservice.entity.ElectionResult;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ElectionResultRepository extends JpaRepository<ElectionResult, Long> {

    /** Seçim sonuçlarını sıralı getir */
    List<ElectionResult> findByElectionIdOrderByRankAsc(Long electionId);

    /** Seçim sonuçları hesaplanmış mı */
    boolean existsByElectionId(Long electionId);

    /** Seçim sonuçlarını sil (yeniden hesaplama için) */
    void deleteByElectionId(Long electionId);
}
