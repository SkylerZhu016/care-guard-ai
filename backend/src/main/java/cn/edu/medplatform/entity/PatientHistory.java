package cn.edu.medplatform.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import lombok.Data;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Entity @Table(name = "patient_histories") @Data
public class PatientHistory {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private Long patientId;
    private String diseaseName;
    private LocalDate diagnosedAt;
    private String note;
    @CreationTimestamp

    private LocalDateTime createdAt;
}
