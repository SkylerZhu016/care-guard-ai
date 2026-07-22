package cn.edu.medplatform.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import lombok.Data;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Entity @Table(name = "rule_hits") @Data
public class RuleHit {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private Long visitId;
    private Long ruleId;
    private Integer ruleVersion;
    private String ruleCode;
    private String ruleName;
    private String category;
    private String riskLevel;
    private String message;
    @Column(columnDefinition = "jsonb") private String evidence;
    @CreationTimestamp

    private LocalDateTime createdAt;
}
