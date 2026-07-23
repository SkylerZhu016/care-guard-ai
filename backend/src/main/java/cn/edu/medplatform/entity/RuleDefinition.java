package cn.edu.medplatform.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import lombok.Data;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Entity @Table(name = "rule_definitions") @Data
public class RuleDefinition {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String code;
    private String name;
    private String category;
    private Integer priority = 100;
    @JdbcTypeCode(org.hibernate.type.SqlTypes.JSON) private String conditionExpr;
    private String message;
    private String riskLevel;
    private Boolean enabled = true;
    private Integer currentVersion = 1;
    private Boolean deleted = false;
    @CreationTimestamp

    private LocalDateTime createdAt;
    @UpdateTimestamp

    private LocalDateTime updatedAt;
}
