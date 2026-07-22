package com.medical.controller;

import com.medical.dto.ApiResponse;
import com.medical.entity.FollowupPlan;
import com.medical.entity.FollowupTask;
import com.medical.service.FollowupService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/followup")
public class FollowupController {

    private final FollowupService followupService;

    public FollowupController(FollowupService followupService) {
        this.followupService = followupService;
    }

    @PostMapping("/plan")
    public ApiResponse<FollowupPlan> createPlan(
            @RequestParam Long visitId,
            @RequestParam String name,
            @RequestParam(defaultValue = "??????") String desc,
            @RequestParam(defaultValue = "7") int intervalDays,
            @RequestParam(defaultValue = "4") int totalTimes) {
        return ApiResponse.success(followupService.createPlan(visitId, name, desc, intervalDays, totalTimes));
    }

    @GetMapping("/plan/{visitId}")
    public ApiResponse<List<FollowupPlan>> getPlans(@PathVariable Long visitId) {
        return ApiResponse.success(followupService.getPlansByVisit(visitId));
    }

    @GetMapping("/tasks/{planId}")
    public ApiResponse<List<FollowupTask>> getTasks(@PathVariable Long planId) {
        return ApiResponse.success(followupService.getTasksByPlan(planId));
    }

    @PutMapping("/tasks/{taskId}/complete")
    public ApiResponse<FollowupTask> completeTask(@PathVariable Long taskId, @RequestParam String response) {
        return ApiResponse.success(followupService.completeTask(taskId, response));
    }
}

