package com.medical.service;

import com.medical.dto.PreConsultRequest;
import com.medical.entity.*;
import com.medical.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Service
public class PreConsultService {

    private final VisitRepository visitRepository;
    private final SimulatedPatientRepository patientRepository;
    private final SymptomRepository symptomRepository;
    private final TriageResultRepository triageResultRepository;

    public PreConsultService(VisitRepository visitRepository,
                             SimulatedPatientRepository patientRepository,
                             SymptomRepository symptomRepository,
                             TriageResultRepository triageResultRepository) {
        this.visitRepository = visitRepository;
        this.patientRepository = patientRepository;
        this.symptomRepository = symptomRepository;
        this.triageResultRepository = triageResultRepository;
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
        return visitRepository.save(visit);
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