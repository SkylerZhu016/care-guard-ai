package cn.edu.medplatform.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import lombok.Data;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Entity @Table(name = "followup_tasks") @Data
public class FollowupTask {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private Long planId;
    private Long patientId;
    private Long assigneeId;
    private String title;
    private String content;
    private LocalDate dueDate;
    private String status = "PENDING";
    private String riskLevel;
    @Version private Integer version;
    private LocalDateTime completedAt;
    @CreationTimestamp

    private LocalDateTime createdAt;
    @UpdateTimestamp

    private LocalDateTime updatedAt;
}
