package cn.edu.medplatform.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import lombok.Data;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Entity @Table(name = "visit_status_logs") @Data
public class VisitStatusLog {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private Long visitId;
    private String fromStatus;
    private String toStatus;
    private Long operatorId;
    private String operatorRole;
    private String reason;
    @CreationTimestamp

    private LocalDateTime createdAt;
}
