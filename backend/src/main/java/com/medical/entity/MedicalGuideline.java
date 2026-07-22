package com.medical.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter
@Entity @Table(name = "medical_guidelines")
public class MedicalGuideline extends BaseEntity {
    @Column(nullable = false, length = 200)
    private String title;

    @Column(length = 100)
    private String category;

    @Column(columnDefinition = "TEXT")
    private String content;

    @Column(columnDefinition = "TEXT")
    private String embeddingVector;  // JSON array string

    @Column(length = 50)
    private String source;

    @Column(length = 20)
    private String version;
}
