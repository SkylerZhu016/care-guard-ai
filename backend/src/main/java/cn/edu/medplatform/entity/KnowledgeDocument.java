package cn.edu.medplatform.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import lombok.Data;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Entity @Table(name = "knowledge_documents") @Data
public class KnowledgeDocument {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String title;
    private String org;
    private LocalDate publishDate;
    private String docType;
    private String scope;
    private String sourceNote;
    private Long fileId;
    private String fileHash;
    private Integer version = 1;
    private String status = "PENDING";
    private Long uploadedBy;
    private Boolean deleted = false;
    @CreationTimestamp

    private LocalDateTime createdAt;
    @UpdateTimestamp

    private LocalDateTime updatedAt;
}
