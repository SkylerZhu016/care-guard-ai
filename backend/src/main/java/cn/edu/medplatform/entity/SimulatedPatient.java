package cn.edu.medplatform.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import lombok.Data;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Entity @Table(name = "simulated_patients") @Data
public class SimulatedPatient {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String patientNo;
    private Long ownerUserId;
    private String name;
    private String gender;
    private LocalDate birthDate;
    private String phone;
    private String idCard;
    private String bloodType;
    @JdbcTypeCode(org.hibernate.type.SqlTypes.JSON) private String chronicTags;
    private String address;
    @CreationTimestamp

    private LocalDateTime createdAt;
    @UpdateTimestamp

    private LocalDateTime updatedAt;
}
