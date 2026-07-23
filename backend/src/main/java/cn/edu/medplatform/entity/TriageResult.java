package cn.edu.medplatform.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import lombok.Data;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Entity @Table(name = "triage_results") @Data
public class TriageResult {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private Long visitId;
    private Long agentRunId;
    private String riskLevel;
    private String riskSummary;
    @JdbcTypeCode(org.hibernate.type.SqlTypes.JSON) private String riskPoints;
    @JdbcTypeCode(org.hibernate.type.SqlTypes.JSON) private String summaryForReview;
    private String safetyStatus;
    private String disclaimer;
    private String knowledgeVersion;
    private String ruleVersion;
    private String modelName;
    private String promptVersion;
    @CreationTimestamp

    private LocalDateTime createdAt;
}
