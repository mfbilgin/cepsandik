package com.cepsandik.communityservice.repository;

import com.cepsandik.communityservice.entity.CommunityMember;
import com.cepsandik.communityservice.enums.MemberRole;
import com.cepsandik.communityservice.enums.MemberStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface CommunityMemberRepository extends JpaRepository<CommunityMember, Long> {

    // Pagination olmadan (eski metodlar)
    List<CommunityMember> findByCommunityIdAndStatus(Long communityId, MemberStatus status);

    List<CommunityMember> findByUserIdAndStatus(String userId, MemberStatus status);

    Optional<CommunityMember> findByCommunityIdAndUserId(Long communityId, String userId);

    boolean existsByCommunityIdAndUserId(Long communityId, String userId);

    long countByCommunityIdAndStatus(Long communityId, MemberStatus status);

    long countByCommunityIdAndRole(Long communityId, MemberRole role);

    List<CommunityMember> findByCommunityIdAndRole(Long communityId, MemberRole role);

    // Pagination ile
    Page<CommunityMember> findByCommunityIdAndStatus(Long communityId, MemberStatus status, Pageable pageable);

    Page<CommunityMember> findByUserIdAndStatus(String userId, MemberStatus status, Pageable pageable);

    // İstatistik için
    @Query("SELECT MAX(m.joinedAt) FROM CommunityMember m WHERE m.communityId = :communityId")
    Optional<LocalDateTime> findLastJoinedAtByCommunityId(@Param("communityId") Long communityId);
}
