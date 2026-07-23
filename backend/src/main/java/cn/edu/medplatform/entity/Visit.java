package cn.edu.medplatform.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import lombok.Data;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Entity @Table(name = "visits") @Data
public class Visit {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String visitNo;
    private Long patientId;
    private Long ownerUserId;
    private String status = "DRAFT";
    private String riskLevel;
    @JdbcTypeCode(org.hibernate.type.SqlTypes.JSON) private String formData;
    @Version private Integer version;
    private Long createdBy;
    private LocalDateTime submittedAt;
    @CreationTimestamp

    private LocalDateTime createdAt;
    @UpdateTimestamp

    private LocalDateTime updatedAt;
}
