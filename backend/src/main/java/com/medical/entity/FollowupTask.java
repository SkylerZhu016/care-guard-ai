package com.medical.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDate;

@Getter @Setter
@Entity @Table(name = "followup_tasks")
public class FollowupTask extends BaseEntity {
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "plan_id", nullable = false)
    private FollowupPlan plan;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assignee_id")
    private User assignee;

    @Column(nullable = false)
    private LocalDate dueDate;

    @Column(columnDefinition = "TEXT")
    private String questionnaire;

    @Column(columnDefinition = "TEXT")
    private String patientResponse;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private TaskStatus status = TaskStatus.PENDING;

    public enum TaskStatus { PENDING, COMPLETED, OVERDUE, SKIPPED }
}
