package com.medical.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import java.util.List;

@Data
public class PreConsultRequest {
    @NotNull private Long patientId;
    @NotBlank private String chiefComplaint;
    private List<SymptomEntry> symptoms;

    @Data
    public static class SymptomEntry {
        @NotBlank private String symptomName;
        private String bodyPart;
        private Integer severity;
        private String duration;
        private String description;
    }
}
