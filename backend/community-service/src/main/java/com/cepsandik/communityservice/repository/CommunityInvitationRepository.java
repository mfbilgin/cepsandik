package com.cepsandik.communityservice.repository;

import com.cepsandik.communityservice.entity.CommunityInvitation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface CommunityInvitationRepository extends JpaRepository<CommunityInvitation, Long> {

    Optional<CommunityInvitation> findByCode(String code);

    List<CommunityInvitation> findByCommunityIdAndIsActiveTrue(Long communityId);

    List<CommunityInvitation> findByExpiresAtBeforeAndIsActiveTrue(LocalDateTime now);

    long countByCommunityIdAndIsActiveTrue(Long communityId);

    @Query("SELECT COALESCE(SUM(i.currentUses), 0) FROM CommunityInvitation i WHERE i.communityId = :communityId")
    long sumCurrentUsesByCommunityId(@Param("communityId") Long communityId);

    @Query("SELECT i FROM CommunityInvitation i WHERE i.isActive = true AND i.maxUses IS NOT NULL AND i.currentUses >= i.maxUses")
    List<CommunityInvitation> findMaxUsedActiveInvitations();
}
