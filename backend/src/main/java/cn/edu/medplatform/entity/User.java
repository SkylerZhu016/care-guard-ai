package cn.edu.medplatform.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import lombok.Data;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Entity @Table(name = "users") @Data
public class User {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String username;
    private String passwordHash;
    private String realName;
    private String phone;
    private String gender;
    private LocalDate birthDate;
    private Boolean enabled = true;
    private Integer failedAttempts = 0;
    private LocalDateTime lockedUntil;
    private Boolean mustChangePassword = false;
    @CreationTimestamp

    private LocalDateTime createdAt;
    @UpdateTimestamp

    private LocalDateTime updatedAt;

    @Transient
    private List<String> roles;
}
