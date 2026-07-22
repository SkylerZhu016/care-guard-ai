package com.example.medsim;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

record IntakeCatalogView(String version, List<CatalogSymptomView> symptoms) {}
record CatalogSymptomView(String code, String name, String category, SupportLevel supportLevel,
                          boolean common, List<CatalogQuestionView> questions) {}
record CatalogQuestionView(String id, String prompt, String type, boolean multiple,
                           List<QuestionOptionView> options) {}
record QuestionOptionView(String value, String label) {}

record PatientProfileInput(
    @Pattern(regexp = "CHILD|ADOLESCENT|ADULT|OLDER_ADULT|UNKNOWN") String ageBand,
    @Pattern(regexp = "NONE|UNKNOWN|PROVIDED") String physiologicalInfoStatus,
    @Size(max = 500) String physiologicalInfo,
    @Pattern(regexp = "NONE|UNKNOWN|PROVIDED") String chronicConditionsStatus,
    List<@Size(max = 100) String> chronicConditions,
    @Pattern(regexp = "NONE|UNKNOWN|PROVIDED") String allergiesStatus,
    List<@Size(max = 100) String> allergies,
    @Pattern(regexp = "NONE|UNKNOWN|PROVIDED") String longTermMedicationsStatus,
    List<@Size(max = 100) String> longTermMedications
) {}
record PatientProfileView(UUID id, UUID ownerId, long version, PatientProfileInput data, OffsetDateTime updatedAt) {}

record QuestionAnswerInput(
    @NotBlank @Size(max = 100) String questionId,
    List<@Size(max = 100) String> selectedOptions,
    @Size(max = 500) String supplementalText
) {}

record SymptomReportInput(
    @NotBlank @Pattern(regexp = "[A-Z][A-Z0-9_]{1,59}") String symptomCode,
    @Size(max = 100) String customName,
    @Pattern(regexp = "CATALOG|RELATED_ANSWER|CUSTOM") String source,
    @Pattern(regexp = "JUST_NOW|TODAY|ONE_TO_THREE_DAYS|MORE_THAN_THREE_DAYS|UNKNOWN") String onsetRange,
    @Pattern(regexp = "CONTINUOUS|INTERMITTENT|RELIEVED|UNKNOWN") String course,
    @Pattern(regexp = "PRESENT|NOT_PRESENT|UNKNOWN") String currentStatus,
    @Pattern(regexp = "NO_IMPACT|NEEDS_REST|UNABLE_NORMAL_ACTIVITY|UNKNOWN") String activityImpact,
    List<@Valid QuestionAnswerInput> answers
) {}

record VisitIntakeV2Input(
    @NotBlank @Pattern(regexp = "[A-Z][A-Z0-9_]{1,59}") String primarySymptomCode,
    @NotBlank @Size(max = 500) String chiefComplaint,
    @Size(max = 2000) String freeText,
    @NotEmpty List<@Valid SymptomReportInput> symptomReports
) {}

record SymptomReportView(UUID id, String symptomCode, String name, String source, String catalogVersion,
                         SupportLevel supportLevel, String onsetRange, String course, String currentStatus,
                         String activityImpact, List<QuestionAnswerInput> answers, Integer legacySeverity,
                         String legacyOnset) {}
record TriageViewV2(UUID id, Urgency ruleUrgency, Urgency aiUrgency, Urgency finalUrgency,
                    CoverageStatus coverageStatus, AssessmentStatus assessmentStatus,
                    List<String> ruleReasons, String aiSummary, ReviewDecision reviewDecision,
                    String reviewReason) {}
record VisitSupplementInput(@NotBlank @Size(max = 2000) String content) {}
record VisitSupplementView(UUID id, String content, OffsetDateTime createdAt) {}
record VisitViewV2(UUID id, UUID ownerId, VisitStatus status, String intakeVersion,
                   String primarySymptomCode, String chiefComplaint, String freeText,
                   List<SymptomReportView> symptomReports, PatientProfileInput profileSnapshot,
                   TriageViewV2 triage, List<AgentRunView> runs, List<VisitSupplementView> supplements,
                   OffsetDateTime createdAt, OffsetDateTime submittedAt) {}
