package com.medical.repository;

import com.medical.entity.TriageResult;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface TriageResultRepository extends JpaRepository<TriageResult, Long> {
    Optional<TriageResult> findByVisitId(Long visitId);
}
