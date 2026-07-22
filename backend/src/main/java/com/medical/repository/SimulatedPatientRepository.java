package com.medical.repository;

import com.medical.entity.SimulatedPatient;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SimulatedPatientRepository extends JpaRepository<SimulatedPatient, Long> {}
