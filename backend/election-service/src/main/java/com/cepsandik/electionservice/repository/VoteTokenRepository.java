package com.cepsandik.electionservice.repository;

import com.cepsandik.electionservice.entity.VoteToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface VoteTokenRepository extends JpaRepository<VoteToken, Long> {

    /** Kullanıcının belirli bir seçimdeki token'ını bul */
    Optional<VoteToken> findByElectionIdAndUserId(Long electionId, String userId);

    /** Token string'i ile bul */
    Optional<VoteToken> findByToken(String token);

    /** Kullanıcı bu seçim için zaten token almış mı */
    boolean existsByElectionIdAndUserId(Long electionId, String userId);
}
