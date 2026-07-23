package cn.edu.medplatform.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import lombok.Data;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Entity @Table(name = "review_records") @Data
public class ReviewRecord {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private Long visitId;
    private Long reviewerId;
    private String action;
    private String comment;
    @JdbcTypeCode(org.hibernate.type.SqlTypes.JSON) private String modifiedSummary;
    private String modifiedRiskLevel;
    @JdbcTypeCode(org.hibernate.type.SqlTypes.JSON) private String aiSnapshot;
    @CreationTimestamp

    private LocalDateTime createdAt;
}
