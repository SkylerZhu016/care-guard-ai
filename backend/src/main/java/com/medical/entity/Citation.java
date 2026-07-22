package com.medical.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter
@Entity @Table(name = "citations")
public class Citation extends BaseEntity {
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "agent_run_id")
    private AgentRun agentRun;

    @Column(columnDefinition = "TEXT")
    private String sourceContent;

    @Column(length = 200)
    private String sourceName;

    @Column(length = 100)
    private String sourceUrl;

    @Column(nullable = false)
    private Integer relevanceScore;
}
