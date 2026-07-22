package com.medical.controller;

import com.medical.dto.ApiResponse;
import com.medical.entity.SafetyAlert;
import com.medical.service.SafetyService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/safety")
public class SafetyController {

    private final SafetyService safetyService;

    public SafetyController(SafetyService safetyService) {
        this.safetyService = safetyService;
    }

    @GetMapping("/alerts")
    public ApiResponse<List<SafetyAlert>> getAlerts(
            @RequestParam(defaultValue = "false") boolean unreviewedOnly) {
        if (unreviewedOnly) {
            return ApiResponse.success(safetyService.getUnreviewedAlerts());
        }
        return ApiResponse.success(safetyService.getCriticalAlerts());
    }

    @GetMapping("/alerts/count")
    public ApiResponse<Long> getUnreviewedCount() {
        return ApiResponse.success(safetyService.getUnreviewedCount());
    }

    @PutMapping("/alerts/{id}/review")
    public ApiResponse<SafetyAlert> review(@PathVariable Long id) {
        return ApiResponse.success(safetyService.reviewAlert(id, null));
    }
}
