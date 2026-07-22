package com.medical.controller;

import com.medical.dto.ApiResponse;
import com.medical.dto.DashboardStats;
import com.medical.service.DashboardService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    private final DashboardService dashboardService;

    public DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @GetMapping("/stats")
    public ApiResponse<DashboardStats> getStats() {
        return ApiResponse.success(dashboardService.getStats());
    }
}
