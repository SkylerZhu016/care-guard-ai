package com.medical.repository;

import com.medical.entity.Symptom;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface SymptomRepository extends JpaRepository<Symptom, Long> {
    List<Symptom> findByVisitIdOrderByOnsetTimeAsc(Long visitId);
}
