package cn.edu.medplatform.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import lombok.Data;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Entity @Table(name = "prompt_versions") @Data
public class PromptVersion {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private Long templateId;
    private Integer version;
    private String content;
    private Boolean enabled = true;
    @CreationTimestamp

    private LocalDateTime createdAt;
}
