package com.cepsandik.userservice.repositories;

import com.cepsandik.userservice.models.AuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {
    Page<AuditLog> findByUserId(UUID userId, Pageable pageable);
    Page<AuditLog> findByModule(String module, Pageable pageable);

    // Faz 3.13 — Pre-existing build break fix: AdminService bu derived
    // query'leri çağırıyordu ama interface'te tanımlı değildi (user-service
    // HEAD'de derlenmiyordu → prod'a çıkamazdı). AuditLog alanları:
    // userId(UUID), action(String), timestamp(LocalDateTime) → isimler geçerli.
    Page<AuditLog> findByUserIdOrderByTimestampDesc(UUID userId, Pageable pageable);
    Page<AuditLog> findByActionContainingIgnoreCaseOrderByTimestampDesc(String action, Pageable pageable);
    // AdminService.getAllAuditLogs — pre-existing build break fix (devam).
    Page<AuditLog> findAllByOrderByTimestampDesc(Pageable pageable);
}
