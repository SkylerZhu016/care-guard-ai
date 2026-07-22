package com.medical.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter
@Entity @Table(name = "safety_alerts")
public class SafetyAlert extends BaseEntity {
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "visit_id")
    private Visit visit;

    @Column(nullable = false, length = 50)
    @Enumerated(EnumType.STRING)
    private AlertType alertType;

    @Column(nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private AlertSeverity severity;

    @Column(length = 200)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(columnDefinition = "TEXT")
    private String evidence;

    private Boolean reviewed = false;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reviewed_by")
    private User reviewedBy;

    public enum AlertType {
        RED_FLAG_SYMPTOM, DRUG_INTERACTION, PROMPT_INJECTION,
        UNAUTHORIZED_DIAGNOSIS, PRIVACY_LEAK, MODEL_HALLUCINATION
    }
    public enum AlertSeverity { INFO, WARNING, CRITICAL }
}
