package com.medical.repository;

import com.medical.entity.SafetyAlert;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface SafetyAlertRepository extends JpaRepository<SafetyAlert, Long> {
    List<SafetyAlert> findByReviewedOrderByCreatedAtDesc(boolean reviewed);
    List<SafetyAlert> findBySeverityOrderByCreatedAtDesc(SafetyAlert.AlertSeverity severity);
    long countByReviewed(boolean reviewed);
}
