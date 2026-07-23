package com.medical.service;

import com.medical.dto.PreConsultRequest;
import com.medical.entity.*;
import com.medical.repository.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.util.List;

@Service
public class PreConsultService {

    private final VisitRepository visitRepository;
    private final SimulatedPatientRepository patientRepository;
    private final SymptomRepository symptomRepository;
    private final TriageResultRepository triageResultRepository;
    private final RestTemplate restTemplate;
    private final String aiServiceUrl;
    private final ObjectMapper objectMapper;

    public PreConsultService(VisitRepository visitRepository,
                             SimulatedPatientRepository patientRepository,
                             SymptomRepository symptomRepository,
                             TriageResultRepository triageResultRepository,
                             RestTemplate restTemplate,
                             @Value("${ai.service.url:http://localhost:8000}") String aiServiceUrl,
                             ObjectMapper objectMapper) {
        this.visitRepository = visitRepository;
        this.patientRepository = patientRepository;
        this.symptomRepository = symptomRepository;
        this.triageResultRepository = triageResultRepository;
        this.restTemplate = restTemplate;
        this.aiServiceUrl = aiServiceUrl;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public Visit submitPreConsult(PreConsultRequest request) {
        var patient = patientRepository.findById(request.getPatientId())
                .orElseThrow(() -> new RuntimeException("Patient not found"));

        var visit = new Visit();
        visit.setPatient(patient);
        visit.setChiefComplaint(request.getChiefComplaint());
        visit.setStatus(Visit.VisitStatus.PENDING);
        visit = visitRepository.save(visit);

        if (request.getSymptoms() != null) {
            for (var se : request.getSymptoms()) {
                var symptom = new Symptom();
                symptom.setVisit(visit);
                symptom.setSymptomName(se.getSymptomName());
                symptom.setBodyPart(se.getBodyPart());
                symptom.setSeverity(se.getSeverity());
                symptom.setDuration(se.getDuration());
                symptom.setDescription(se.getDescription());
                symptomRepository.save(symptom);
            }
        }

        visit.setStatus(Visit.VisitStatus.TRIAGING);
        visit = visitRepository.save(visit);

        // Call AI service for triage analysis
        try {
            callAiTriage(request, patient, visit);
        } catch (Exception e) {
            throw new RuntimeException("AI triage failed: " + e.getMessage(), e);
        }

        return visit;
    }

    private void callAiTriage(PreConsultRequest request, SimulatedPatient patient, Visit visit) {
        // Build request body for AI service
        var aiRequest = objectMapper.createObjectNode();
        aiRequest.put("patient_id", patient.getId().intValue());
        aiRequest.put("patient_name", patient.getName());
        aiRequest.put("patient_age", patient.getAge());
        aiRequest.put("patient_gender", patient.getGender());
        aiRequest.put("patient_history", patient.getMedicalHistory() != null ? patient.getMedicalHistory() : "");
        aiRequest.put("patient_allergies", patient.getAllergies() != null ? patient.getAllergies() : "");
        aiRequest.put("chief_complaint", request.getChiefComplaint() != null ? request.getChiefComplaint() : "");

        var symptomsArray = aiRequest.putArray("symptoms");
        if (request.getSymptoms() != null) {
            for (var se : request.getSymptoms()) {
                var symptomNode = objectMapper.createObjectNode();
                symptomNode.put("symptom_name", se.getSymptomName() != null ? se.getSymptomName() : "");
                symptomNode.put("body_part", se.getBodyPart() != null ? se.getBodyPart() : "");
                symptomNode.put("severity", se.getSeverity() != null ? se.getSeverity() : 5);
                symptomNode.put("duration", se.getDuration() != null ? se.getDuration() : "");
                symptomNode.put("description", se.getDescription() != null ? se.getDescription() : "");
                symptomsArray.add(symptomNode);
            }
        }

        // Call AI service /ai/consult/submit (triage + safety)
        var url = aiServiceUrl + "/ai/consult/submit";
        var response = restTemplate.postForObject(url, aiRequest, JsonNode.class);

        if (response == null) {
            throw new RuntimeException("AI service returned null response");
        }

        // Extract triage result
        var triageNode = response.path("data").path("triage");
        int riskScore = triageNode.path("risk_score").asInt(0);
        String riskLevel = triageNode.path("risk_level").asText("LOW");
        String recommendations = triageNode.path("recommendations").asText("");
        boolean safetyChecked = triageNode.path("safety_checked").asBoolean(false);
        String safetyReport = triageNode.path("safety_report").asText("");

        // Build comma-separated strings for list fields
        String riskFactors = joinJsonArray(triageNode.path("risk_factors"));
        String redFlags = joinJsonArray(triageNode.path("red_flags"));

        // Save TriageResult
        var triageResult = new TriageResult();
        triageResult.setVisit(visit);
        triageResult.setRiskScore(riskScore);
        triageResult.setRiskLevel(riskLevel);
        triageResult.setRiskFactors(riskFactors);
        triageResult.setRedFlags(redFlags);
        triageResult.setRecommendations(recommendations);
        triageResult.setSafetyChecked(safetyChecked);
        triageResult.setSafetyReport(safetyReport);
        triageResultRepository.save(triageResult);

        // Update visit: map risk level string to integer and set status
        visit.setRiskLevel(mapRiskLevel(riskLevel));
        visit.setTriageResult(riskLevel + " - " + recommendations);
        visit.setStatus(Visit.VisitStatus.REVIEW_REQUIRED);
        visitRepository.save(visit);
    }

    private int mapRiskLevel(String level) {
        return switch (level) {
            case "CRITICAL" -> 5;
            case "HIGH" -> 4;
            case "MEDIUM" -> 3;
            default -> 2; // LOW
        };
    }

    private String joinJsonArray(JsonNode array) {
        if (array == null || !array.isArray()) return "";
        var sb = new StringBuilder();
        for (var element : array) {
            if (sb.length() > 0) sb.append("; ");
            sb.append(element.asText());
        }
        return sb.toString();
    }

    public List<Visit> getPendingVisits() {
        return visitRepository.findByStatusOrderByCreatedAtDesc(Visit.VisitStatus.REVIEW_REQUIRED);
    }

    public TriageResult getTriageResult(Long visitId) {
        return triageResultRepository.findByVisitId(visitId).orElse(null);
    }

    public List<Symptom> getSymptoms(Long visitId) {
        return symptomRepository.findByVisitIdOrderByOnsetTimeAsc(visitId);
    }

    @Transactional
    public Visit approveVisit(Long visitId, String notes) {
        var visit = visitRepository.findById(visitId)
                .orElseThrow(() -> new RuntimeException("Visit not found"));
        visit.setDoctorNotes(notes);
        visit.setStatus(Visit.VisitStatus.APPROVED);
        return visitRepository.save(visit);
    }
}
