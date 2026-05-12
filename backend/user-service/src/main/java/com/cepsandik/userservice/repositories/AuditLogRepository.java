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
}
