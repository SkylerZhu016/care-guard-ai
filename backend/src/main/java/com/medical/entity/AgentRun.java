package com.medical.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter
@Entity @Table(name = "agent_runs")
public class AgentRun extends BaseEntity {
    @Column(nullable = false, length = 50)
    private String runId;

    @Column(length = 100)
    private String agentName;

    @Column(length = 50)
    private String modelVersion;

    @Column(length = 50)
    private String promptVersion;

    @Column(length = 50)
    private String kbVersion;

    @Column(columnDefinition = "TEXT")
    private String inputData;

    @Column(columnDefinition = "TEXT")
    private String outputData;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private RunStatus status;

    @Column(columnDefinition = "TEXT")
    private String errorLog;

    private Long latencyMs;

    public enum RunStatus { SUCCESS, FAILED, TIMEOUT, REVIEW_REQUIRED }
}
