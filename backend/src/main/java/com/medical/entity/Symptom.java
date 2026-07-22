package com.medical.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;

@Getter @Setter
@Entity @Table(name = "symptoms")
public class Symptom extends BaseEntity {
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "visit_id", nullable = false)
    private Visit visit;

    @Column(nullable = false, length = 100)
    private String symptomName;

    @Column(length = 20)
    private String bodyPart;

    private Integer severity;  // 1-10

    @Column(length = 50)
    private String duration;

    @Column(columnDefinition = "TEXT")
    private String description;

    private LocalDateTime onsetTime;
}
