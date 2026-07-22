package cn.edu.medplatform.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import lombok.Data;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Entity @Table(name = "citations") @Data
public class Citation {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private Long visitId;
    private Long agentRunId;
    private Long chunkId;
    private Long documentId;
    private String title;
    private String section;
    private Integer pageNo;
    private String snippet;
    private Double score;
    private String knowledgeVersion;
    @CreationTimestamp

    private LocalDateTime createdAt;
}
