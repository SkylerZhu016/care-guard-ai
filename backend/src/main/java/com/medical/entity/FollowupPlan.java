package com.medical.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDate;

@Getter @Setter
@Entity @Table(name = "followup_plans")
public class FollowupPlan extends BaseEntity {
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "visit_id", nullable = false)
    private Visit visit;

    @Column(nullable = false, length = 200)
    private String planName;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(nullable = false)
    private Integer intervalDays;

    @Column(nullable = false)
    private Integer totalTimes;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private PlanStatus status = PlanStatus.ACTIVE;

    public enum PlanStatus { ACTIVE, PAUSED, COMPLETED, CANCELLED }
}
