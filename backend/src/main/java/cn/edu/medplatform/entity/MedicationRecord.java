package cn.edu.medplatform.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import lombok.Data;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Entity @Table(name = "medication_records") @Data
public class MedicationRecord {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private Long patientId;
    private String drugName;
    private String dosage;
    private String frequency;
    private LocalDate startDate;
    private LocalDate endDate;
    @CreationTimestamp

    private LocalDateTime createdAt;
}
