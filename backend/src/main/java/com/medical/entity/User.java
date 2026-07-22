package com.medical.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.util.Set;

@Getter @Setter
@Entity @Table(name = "users")
public class User extends BaseEntity {
    @Column(nullable = false, unique = true, length = 50)
    private String username;

    @Column(nullable = false)
    private String password;

    @Column(length = 100)
    private String displayName;

    @Column(length = 20)
    private String phone;

    @Column(length = 100)
    private String email;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private Role role;

    private boolean enabled = true;

    public enum Role {
        ROLE_PATIENT, ROLE_DOCTOR, ROLE_FOLLOWUP, ROLE_ADMIN
    }
}
