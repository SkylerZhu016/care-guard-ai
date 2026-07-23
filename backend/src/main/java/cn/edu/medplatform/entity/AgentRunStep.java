package cn.edu.medplatform.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import lombok.Data;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Entity @Table(name = "agent_run_steps") @Data
public class AgentRunStep {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private Long runId;
    private String step;
    private String status = "RUNNING";
    @JdbcTypeCode(org.hibernate.type.SqlTypes.JSON) private String inputJson;
    @JdbcTypeCode(org.hibernate.type.SqlTypes.JSON) private String outputJson;
    private Integer tokens = 0;
    private Integer durationMs;
    private String error;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
    @CreationTimestamp

    private LocalDateTime createdAt;
}
