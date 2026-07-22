package cn.edu.medplatform.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.UpdateTimestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Entity @Table(name = "system_configs") @Data
public class SystemConfig {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String configKey;
    private String configValue;
    private String description;
    @UpdateTimestamp
    private LocalDateTime updatedAt;
}
