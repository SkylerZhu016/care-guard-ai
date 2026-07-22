package com.medical.controller;

import com.medical.dto.ApiResponse;
import com.medical.dto.PreConsultRequest;
import com.medical.entity.Symptom;
import com.medical.entity.TriageResult;
import com.medical.entity.Visit;
import com.medical.service.PreConsultService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/consult")
public class PreConsultController {

    private final PreConsultService consultService;

    public PreConsultController(PreConsultService consultService) {
        this.consultService = consultService;
    }

    @PostMapping("/submit")
    public ApiResponse<Visit> submit(@Valid @RequestBody PreConsultRequest request) {
        return ApiResponse.success(consultService.submitPreConsult(request));
    }

    @GetMapping("/pending")
    public ApiResponse<List<Visit>> getPending() {
        return ApiResponse.success(consultService.getPendingVisits());
    }

    @GetMapping("/{visitId}/triage")
    public ApiResponse<TriageResult> getTriage(@PathVariable Long visitId) {
        return ApiResponse.success(consultService.getTriageResult(visitId));
    }

    @GetMapping("/{visitId}/symptoms")
    public ApiResponse<List<Symptom>> getSymptoms(@PathVariable Long visitId) {
        return ApiResponse.success(consultService.getSymptoms(visitId));
    }

    @PutMapping("/{visitId}/approve")
    public ApiResponse<Visit> approve(@PathVariable Long visitId, @RequestParam String notes) {
        return ApiResponse.success(consultService.approveVisit(visitId, notes));
    }
}
