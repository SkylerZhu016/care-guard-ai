package cn.edu.medplatform.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import lombok.Data;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Entity @Table(name = "uploaded_files") @Data
public class UploadedFile {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String fileName;
    private String objectKey;
    private String bucket;
    private String contentType;
    private Long size;
    private String sha256;
    private Long uploadedBy;
    @CreationTimestamp

    private LocalDateTime createdAt;
}
