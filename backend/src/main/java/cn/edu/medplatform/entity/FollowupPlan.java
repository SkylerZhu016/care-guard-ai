package cn.edu.medplatform.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import lombok.Data;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Entity @Table(name = "followup_plans") @Data
public class FollowupPlan {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private Long patientId;
    private Long visitId;
    private Long createdBy;
    private String planName;
    private String status = "DRAFT";
    private Integer intervalDays = 7;
    private LocalDate startDate;
    private String endCondition;
    @JdbcTypeCode(org.hibernate.type.SqlTypes.JSON) private String items;
    @Version private Integer version;
    @CreationTimestamp

    private LocalDateTime createdAt;
    @UpdateTimestamp

    private LocalDateTime updatedAt;
    @Transient private java.util.List<FollowupTask> tasks;
}
