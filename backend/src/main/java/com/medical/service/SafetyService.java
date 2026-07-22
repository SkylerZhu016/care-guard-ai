package com.medical.service;

import com.medical.entity.SafetyAlert;
import com.medical.entity.User;
import com.medical.repository.SafetyAlertRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class SafetyService {

    private final SafetyAlertRepository alertRepository;

    public SafetyService(SafetyAlertRepository alertRepository) {
        this.alertRepository = alertRepository;
    }

    public SafetyAlert createAlert(SafetyAlert alert) {
        return alertRepository.save(alert);
    }

    public List<SafetyAlert> getUnreviewedAlerts() {
        return alertRepository.findByReviewedOrderByCreatedAtDesc(false);
    }

    public List<SafetyAlert> getCriticalAlerts() {
        return alertRepository.findBySeverityOrderByCreatedAtDesc(SafetyAlert.AlertSeverity.CRITICAL);
    }

    public long getUnreviewedCount() {
        return alertRepository.countByReviewed(false);
    }

    public SafetyAlert reviewAlert(Long alertId, User reviewer) {
        var alert = alertRepository.findById(alertId)
                .orElseThrow(() -> new RuntimeException("Alert not found"));
        alert.setReviewed(true);
        alert.setReviewedBy(reviewer);
        return alertRepository.save(alert);
    }
}