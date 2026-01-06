package com.cepsandik.communityservice.repository;

import com.cepsandik.communityservice.entity.Community;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CommunityRepository extends JpaRepository<Community, Long> {

    List<Community> findByOwnerIdAndIsDeletedFalse(String ownerId);

    Optional<Community> findByIdAndIsDeletedFalse(Long id);

    boolean existsByNameAndOwnerIdAndIsDeletedFalse(String name, String ownerId);

    // Arama ve filtreleme
    @Query("SELECT c FROM Community c WHERE c.isDeleted = false AND " +
            "(LOWER(c.name) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
            "LOWER(c.description) LIKE LOWER(CONCAT('%', :query, '%')))")
    Page<Community> searchCommunities(@Param("query") String query, Pageable pageable);

    // Kullanıcının üye olduğu topluluklar için pagination
    @Query("SELECT c FROM Community c WHERE c.id IN :communityIds AND c.isDeleted = false")
    Page<Community> findByIdInAndIsDeletedFalse(@Param("communityIds") List<Long> communityIds, Pageable pageable);
}
