package cn.edu.medplatform.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import lombok.Data;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Entity @Table(name = "agent_runs") @Data
public class AgentRun {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private Long visitId;
    private String status = "PENDING";
    private String currentStep;
    private String triggerType = "SUBMIT";
    private String errorMessage;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
    private Integer totalTokens = 0;
    private String modelName;
    @Column(columnDefinition = "jsonb") private String promptVersions;
    private String knowledgeVersion;
    private String ruleVersion;
    @CreationTimestamp

    private LocalDateTime createdAt;
    @UpdateTimestamp

    private LocalDateTime updatedAt;
    @Transient private java.util.List<AgentRunStep> steps;
}
