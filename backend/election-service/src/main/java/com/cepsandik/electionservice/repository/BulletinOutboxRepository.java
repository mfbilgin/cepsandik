package com.cepsandik.electionservice.repository;

import com.cepsandik.electionservice.entity.BulletinOutbox;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface BulletinOutboxRepository extends JpaRepository<BulletinOutbox, Long> {

    /** Yayımlanmamış kayıtlar, id sırasıyla (hash-zinciri sırası korunur). */
    @Query("SELECT o FROM BulletinOutbox o WHERE o.published = false ORDER BY o.id ASC")
    List<BulletinOutbox> findPendingOrdered(Pageable pageable);

    /** Bu election'da yayımlanmamış KRİTİK şeffaflık kaydı sayısı (>0 ise arşiv/sonuç bloklanır). */
    @Query("SELECT COUNT(o) FROM BulletinOutbox o WHERE o.electionId = :electionId "
            + "AND o.published = false AND o.critical = true")
    long countUnpublishedCritical(@Param("electionId") String electionId);
}
