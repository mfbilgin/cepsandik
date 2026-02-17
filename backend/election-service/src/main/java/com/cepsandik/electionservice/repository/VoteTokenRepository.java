package com.cepsandik.electionservice.repository;

import com.cepsandik.electionservice.entity.VoteToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface VoteTokenRepository extends JpaRepository<VoteToken, Long> {

    /** Kullanıcının belirli bir seçimdeki token'ını bul */
    Optional<VoteToken> findByElectionIdAndUserId(Long electionId, String userId);

    /** Token string'i ile bul */
    Optional<VoteToken> findByToken(String token);

    /** Kullanıcı bu seçim için zaten token almış mı */
    boolean existsByElectionIdAndUserId(Long electionId, String userId);

    /** Kullanıcının tüm token'larını getir (seçim bilgisiyle) */
    List<VoteToken> findByUserIdOrderByCreatedAtDesc(String userId);

    /** Kullanıcının oy kullandığı token'lar (katılım geçmişi) */
    List<VoteToken> findByUserIdAndIsUsedTrueOrderByUsedAtDesc(String userId);

    /** Kullanıcının henüz oy kullanmadığı token'lar (aktif seçimlerim) */
    List<VoteToken> findByUserIdAndIsUsedFalseOrderByCreatedAtDesc(String userId);

    /** Belirli bir seçimdeki toplam token sayısı (katılımcı sayısı) */
    long countByElectionId(Long electionId);

    /** Belirli bir seçimde kullanılmış token sayısı (oy kullanan) */
    long countByElectionIdAndIsUsedTrue(Long electionId);
}

