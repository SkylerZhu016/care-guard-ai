package com.medical.service;

import com.medical.dto.DashboardStats;
import com.medical.entity.SafetyAlert;
import com.medical.entity.Visit;
import com.medical.repository.*;
import org.springframework.stereotype.Service;

import java.time.format.DateTimeFormatter;
import java.util.stream.Collectors;

@Service
public class DashboardService {

    private final VisitRepository visitRepository;
    private final FollowupPlanRepository planRepository;
    private final FollowupTaskRepository taskRepository;
    private final SafetyAlertRepository alertRepository;

    public DashboardService(VisitRepository visitRepository,
                            FollowupPlanRepository planRepository,
                            FollowupTaskRepository taskRepository,
                            SafetyAlertRepository alertRepository) {
        this.visitRepository = visitRepository;
        this.planRepository = planRepository;
        this.taskRepository = taskRepository;
        this.alertRepository = alertRepository;
    }

    public DashboardStats getStats() {
        var stats = new DashboardStats();
        stats.setPendingVisits(visitRepository.countByStatus(Visit.VisitStatus.REVIEW_REQUIRED));
        stats.setActiveFollowups(planRepository.findByStatus(com.medical.entity.FollowupPlan.PlanStatus.ACTIVE).size());
        stats.setUnreviewedAlerts(alertRepository.countByReviewed(false));
        stats.setCriticalAlerts(alertRepository.findBySeverityOrderByCreatedAtDesc(SafetyAlert.AlertSeverity.CRITICAL).size());

        var fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
        var recentVisits = visitRepository.findByStatusOrderByCreatedAtDesc(Visit.VisitStatus.REVIEW_REQUIRED)
                .stream().limit(5).map(v -> {
                    var s = new DashboardStats.VisitSummary();
                    s.setId(v.getId());
                    s.setPatientName(v.getPatient() != null ? v.getPatient().getName() : "Unknown");
                    s.setStatus(v.getStatus().name());
                    s.setRiskLevel(v.getRiskLevel());
                    s.setCreatedAt(v.getCreatedAt() != null ? v.getCreatedAt().format(fmt) : "");
                    return s;
                }).collect(Collectors.toList());
        stats.setRecentVisits(recentVisits);

        var recentAlerts = alertRepository.findByReviewedOrderByCreatedAtDesc(false)
                .stream().limit(5).map(a -> {
                    var s = new DashboardStats.AlertSummary();
                    s.setId(a.getId());
                    s.setTitle(a.getTitle());
                    s.setSeverity(a.getSeverity().name());
                    s.setCreatedAt(a.getCreatedAt() != null ? a.getCreatedAt().format(fmt) : "");
                    return s;
                }).collect(Collectors.toList());
        stats.setRecentAlerts(recentAlerts);

        return stats;
    }
}
