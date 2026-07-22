package com.medical.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter
@Entity @Table(name = "audit_logs")
public class AuditLog extends BaseEntity {
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @Column(nullable = false, length = 50)
    private String action;

    @Column(length = 100)
    private String resource;

    @Column(length = 100)
    private String resourceId;

    @Column(columnDefinition = "TEXT")
    private String detail;

    @Column(length = 45)
    private String ipAddress;

    @Column(nullable = false)
    private Boolean success = true;
}
