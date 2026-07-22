package com.medical.dto;

import lombok.Data;
import java.util.List;

@Data
public class DashboardStats {
    private long pendingVisits;
    private long activeFollowups;
    private long unreviewedAlerts;
    private long criticalAlerts;
    private List<VisitSummary> recentVisits;
    private List<AlertSummary> recentAlerts;

    @Data
    public static class VisitSummary {
        private Long id;
        private String patientName;
        private String status;
        private Integer riskLevel;
        private String createdAt;
    }

    @Data
    public static class AlertSummary {
        private Long id;
        private String title;
        private String severity;
        private String createdAt;
    }
}
