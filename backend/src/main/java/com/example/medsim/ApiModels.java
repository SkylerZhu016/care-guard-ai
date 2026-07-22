package com.example.medsim;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

record LoginRequest(@NotBlank String username, @NotBlank String password) {}
record AuthResponse(String accessToken, String tokenType, long expiresIn, UserView user) {}
record UserView(UUID id, String username, String displayName, Role role) {}
record SymptomInput(@NotBlank String code, @NotBlank String name, @Min(0) @Max(10) int severity, String onset) {}
record VisitInput(@NotBlank @Size(max=500) String chiefComplaint, @Size(max=2000) String freeText, @NotEmpty List<@Valid SymptomInput> symptoms) {}
record ReviewInput(@NotBlank String decision, @NotBlank @Size(max=1000) String reason, Urgency finalUrgency) {}
record PlanInput(@NotNull UUID visitId, String templateCode) {}
record TaskUpdate(@NotNull TaskStatus status, @Size(max=1000) String resultSummary) {}
record RuleOutcome(Urgency urgency, List<String> reasonCodes) {}
record CitationView(String guidelineId, String chunkId, String claimKey, String title, String section, String quote) {}
record AgentRunView(String runId, String status, String provider, String model, String safetyDecision, List<String> safetyReasons, Long durationMs, String errorCode, List<CitationView> citations) {}
record TriageView(UUID id, Urgency ruleUrgency, Urgency aiUrgency, Urgency finalUrgency, List<String> ruleReasons, String aiSummary, String reviewDecision, String reviewReason) {}
record VisitView(UUID id, UUID ownerId, VisitStatus status, String chiefComplaint, String freeText, List<SymptomInput> symptoms, TriageView triage, List<AgentRunView> runs, OffsetDateTime createdAt, OffsetDateTime submittedAt) {}
record PlanView(UUID id, UUID visitId, PlanStatus status, String templateCode, OffsetDateTime activatedAt, List<TaskView> tasks) {}
record TaskView(UUID id, UUID planId, String taskCode, String title, OffsetDateTime dueAt, TaskStatus status, String resultSummary) {}
record AlertView(UUID id, String runId, UUID visitId, String category, String severity, List<String> reasonCodes, String redactedSummary, String status, OffsetDateTime createdAt) {}
record AuditView(UUID id, String action, String targetType, UUID targetId, String requestId, String result, String metadata, OffsetDateTime createdAt) {}

