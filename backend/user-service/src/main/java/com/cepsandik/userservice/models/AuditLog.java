package com.cepsandik.userservice.models;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "audit_logs")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id")
    private UUID userId;

    @Column(nullable = false)
    private String action;

    @Column(nullable = false)
    private String module; // AUTH, USER, MAIL, PROFILE, etc.

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private LogLevel level; // INFO, WARN, ERROR, CRITICAL

    @Column(columnDefinition = "TEXT")
    private String details;

    @Column(name = "ip_address")
    private String ipAddress;

    @Column(name = "user_agent")
    private String userAgent;

    @Column(nullable = false)
    private String status; // SUCCESS, FAILURE

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime timestamp;

    public enum LogLevel {
        INFO, WARN, ERROR, CRITICAL
    }
}