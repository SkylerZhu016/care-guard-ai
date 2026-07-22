package com.medical.repository;

import com.medical.entity.Visit;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface VisitRepository extends JpaRepository<Visit, Long> {
    List<Visit> findByStatusOrderByCreatedAtDesc(Visit.VisitStatus status);
    List<Visit> findByDoctorIdOrderByCreatedAtDesc(Long doctorId);
    List<Visit> findByPatientIdOrderByCreatedAtDesc(Long patientId);
    long countByStatus(Visit.VisitStatus status);
}
