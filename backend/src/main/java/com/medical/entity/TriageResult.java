package com.medical.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter
@Entity @Table(name = "triage_results")
public class TriageResult extends BaseEntity {
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "visit_id", nullable = false)
    private Visit visit;

    @Column(nullable = false)
    private Integer riskScore;

    @Column(length = 20)
    private String riskLevel;  // LOW, MEDIUM, HIGH, CRITICAL

    @Column(columnDefinition = "TEXT")
    private String riskFactors;

    @Column(columnDefinition = "TEXT")
    private String redFlags;

    @Column(columnDefinition = "TEXT")
    private String recommendations;

    private Boolean safetyChecked = false;

    @Column(columnDefinition = "TEXT")
    private String safetyReport;
}
