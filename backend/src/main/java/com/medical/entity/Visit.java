package com.medical.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter
@Entity @Table(name = "visits")
public class Visit extends BaseEntity {
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "patient_id", nullable = false)
    private SimulatedPatient patient;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "doctor_id")
    private User doctor;

    @Column(nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private VisitStatus status = VisitStatus.PENDING;

    @Column(columnDefinition = "TEXT")
    private String chiefComplaint;

    @Column(columnDefinition = "TEXT")
    private String structuredSymptoms;  // JSON

    private Integer riskLevel;  // 1-5

    @Column(columnDefinition = "TEXT")
    private String triageResult;

    @Column(columnDefinition = "TEXT")
    private String doctorNotes;

    @Column(length = 50)
    private String agentRunId;

    public enum VisitStatus {
        PENDING, TRIAGING, REVIEW_REQUIRED, APPROVED, FOLLOWUP, CLOSED
    }
}
