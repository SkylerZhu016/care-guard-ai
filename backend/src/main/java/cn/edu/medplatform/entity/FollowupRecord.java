package cn.edu.medplatform.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import lombok.Data;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Entity @Table(name = "followup_records") @Data
public class FollowupRecord {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private Long taskId;
    private Long patientId;
    private String contactResult;
    private String symptomChange;
    private String note;
    private String feedback;
    private Long recordedBy;
    @CreationTimestamp

    private LocalDateTime createdAt;
}
