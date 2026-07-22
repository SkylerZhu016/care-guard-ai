package cn.edu.medplatform.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import lombok.Data;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Entity @Table(name = "audit_logs") @Data
public class AuditLog {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private Long userId;
    private String username;
    private String role;
    private String action;
    private String objectType;
    private String objectId;
    private String beforeSummary;
    private String afterSummary;
    private String ip;
    private String traceId;
    @CreationTimestamp

    private LocalDateTime createdAt;
}
