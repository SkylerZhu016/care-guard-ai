package com.medical.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDate;

@Getter @Setter
@Entity @Table(name = "simulated_patients")
public class SimulatedPatient extends BaseEntity {
    @Column(nullable = false, length = 50)
    private String name;

    @Column(nullable = false)
    private Integer age;

    @Column(nullable = false, length = 10)
    private String gender;

    @Column(columnDefinition = "TEXT")
    private String medicalHistory;

    @Column(columnDefinition = "TEXT")
    private String allergies;

    @Column(length = 20)
    private String bloodType;

    @Column(columnDefinition = "TEXT")
    private String syntheticProfile;  // JSON profile for simulation
}
