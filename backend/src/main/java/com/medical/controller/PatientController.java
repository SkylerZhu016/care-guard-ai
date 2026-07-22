package com.medical.controller;

import com.medical.dto.ApiResponse;
import com.medical.entity.SimulatedPatient;
import com.medical.repository.SimulatedPatientRepository;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/patients")
public class PatientController {

    private final SimulatedPatientRepository patientRepository;

    public PatientController(SimulatedPatientRepository patientRepository) {
        this.patientRepository = patientRepository;
    }

    @GetMapping("/list")
    public ApiResponse<List<SimulatedPatient>> list() {
        return ApiResponse.success(patientRepository.findAll());
    }
}
