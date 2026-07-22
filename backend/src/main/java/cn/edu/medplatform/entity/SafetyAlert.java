package cn.edu.medplatform.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import lombok.Data;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Entity @Table(name = "safety_alerts") @Data
public class SafetyAlert {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String type;
    private String level = "MEDIUM";
    private Long visitId;
    private Long agentRunId;
    private String description;
    @Column(columnDefinition = "jsonb") private String detail;
    private String status = "OPEN";
    private Long handledBy;
    private LocalDateTime handledAt;
    private String handleNote;
    @CreationTimestamp

    private LocalDateTime createdAt;
}
