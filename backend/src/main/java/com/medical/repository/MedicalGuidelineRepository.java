package com.medical.repository;

import com.medical.entity.MedicalGuideline;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface MedicalGuidelineRepository extends JpaRepository<MedicalGuideline, Long> {
    List<MedicalGuideline> findByCategory(String category);
}
