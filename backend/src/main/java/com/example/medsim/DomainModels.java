package com.example.medsim;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.OffsetDateTime;
import java.util.UUID;

enum Role { PATIENT, CLINICIAN, FOLLOWUP_STAFF, ADMIN }
enum VisitStatus { DRAFT, SUBMITTED, PROCESSING, PENDING_REVIEW, REVIEWED, FOLLOWUP_ACTIVE, CLOSED, REJECTED }
enum Urgency { ROUTINE, URGENT, EMERGENCY }
enum SupportLevel { RULE_SUPPORTED, RECORD_ONLY, CUSTOM }
enum CoverageStatus { FULL, PARTIAL, NONE }
enum AssessmentStatus { RULE_EVALUATED, REQUIRES_MANUAL_REVIEW }
enum ReviewDecision { ACCEPT, MODIFY, REJECT }
enum PlanStatus { DRAFT, ACTIVE, PAUSED, COMPLETED, CANCELLED }
enum TaskStatus { PENDING, IN_PROGRESS, COMPLETED, OVERDUE, CANCELLED }

@Entity @Table(name = "users")
class UserAccount {
    @Id UUID id;
    @Column(nullable = false, unique = true) String username;
    @Column(name = "display_name", nullable = false) String displayName;
    @Column(name = "password_hash", nullable = false) String passwordHash;
    @Enumerated(EnumType.STRING) @Column(nullable = false) Role role;
    @Column(nullable = false) boolean enabled = true;
    @Column(name = "created_at", nullable = false) OffsetDateTime createdAt = OffsetDateTime.now();

    static UserAccount create(String username, String displayName, String passwordHash, Role role) {
        var value = new UserAccount(); value.id = UUID.randomUUID(); value.username = username;
        value.displayName = displayName; value.passwordHash = passwordHash; value.role = role; return value;
    }
}

@Entity @Table(name = "visits")
class Visit {
    @Id UUID id;
    @Column(name = "owner_id", nullable = false) UUID ownerId;
    @Enumerated(EnumType.STRING) @Column(nullable = false) VisitStatus status = VisitStatus.DRAFT;
    @Column(name = "chief_complaint", nullable = false) String chiefComplaint;
    @Column(name = "free_text", nullable = false) String freeText = "";
    @Column(name = "intake_version", nullable = false) String intakeVersion = "INTAKE_V1";
    @Column(name = "primary_symptom_code") String primarySymptomCode;
    @JdbcTypeCode(SqlTypes.JSON) @Column(name = "profile_snapshot", columnDefinition = "jsonb") String profileSnapshot;
    @Column(name = "submitted_at") OffsetDateTime submittedAt;
    @Column(name = "idempotency_key", unique = true) String idempotencyKey;
    @Version long version;
    @Column(name = "created_at", nullable = false) OffsetDateTime createdAt = OffsetDateTime.now();
}

@Entity @Table(name = "symptoms")
class SymptomEntity {
    @Id UUID id;
    @Column(name = "visit_id", nullable = false) UUID visitId;
    @Column(name = "code_system", nullable = false) String codeSystem = "LOCAL_SYMPTOM_V1";
    @Column(nullable = false) String code;
    @Column(nullable = false) String name;
    @Column(name = "legacy_severity") Integer legacySeverity;
    @Column(name = "onset_text") String onset;
    @Column(name = "catalog_version") String catalogVersion;
    @Enumerated(EnumType.STRING) @Column(name = "support_level") SupportLevel supportLevel;
    @Column(name = "report_source") String reportSource;
    @Column(name = "onset_range") String onsetRange;
    @Column String course;
    @Column(name = "current_status") String currentStatus;
    @Column(name = "activity_impact") String activityImpact;
    @JdbcTypeCode(SqlTypes.JSON) @Column(name = "answers_json", columnDefinition = "jsonb") String answersJson;
}

@Entity @Table(name = "triage_results")
class TriageResult {
    @Id UUID id;
    @Column(name = "visit_id", nullable = false, unique = true) UUID visitId;
    @Enumerated(EnumType.STRING) @Column(name = "rule_urgency") Urgency ruleUrgency;
    @Enumerated(EnumType.STRING) @Column(name = "ai_urgency") Urgency aiUrgency;
    @Enumerated(EnumType.STRING) @Column(name = "final_urgency") Urgency finalUrgency;
    @Column(name = "rule_reason_codes", nullable = false) String ruleReasonCodes;
    @Enumerated(EnumType.STRING) @Column(name = "coverage_status") CoverageStatus coverageStatus;
    @Enumerated(EnumType.STRING) @Column(name = "assessment_status") AssessmentStatus assessmentStatus;
    @Column(name = "ai_summary") String aiSummary;
    @Enumerated(EnumType.STRING) @Column(name = "review_decision") ReviewDecision reviewDecision;
    @Column(name = "review_reason") String reviewReason;
    @Column(name = "reviewer_id") UUID reviewerId;
    @Column(name = "reviewed_at") OffsetDateTime reviewedAt;
    @Version long version;
}

@Entity @Table(name = "agent_runs")
class AgentRun {
    @Id UUID id;
    @Column(name = "run_id", nullable = false, unique = true) String runId;
    @Column(name = "visit_id", nullable = false) UUID visitId;
    @Column(nullable = false) String status;
    @Column(nullable = false) String provider = "fake";
    @Column(name = "model_name", nullable = false) String modelName = "fake-v1";
    @Column(name = "prompt_version", nullable = false) String promptVersion = "triage-v1";
    @Column(name = "knowledge_base_version", nullable = false) String knowledgeBaseVersion = "demo-kb-v1";
    @Column(name = "rule_set_version", nullable = false) String ruleSetVersion = "red-flags-v1";
    @Column(name = "output_hash") String outputHash;
    @Column(name = "safety_decision") String safetyDecision;
    @Column(name = "safety_reason_codes") String safetyReasonCodes;
    @Column(name = "duration_ms") Long durationMs;
    @Column(name = "error_code") String errorCode;
    @Column(name = "agent_trace") String agentTrace;
    @Column(name = "created_at", nullable = false) OffsetDateTime createdAt = OffsetDateTime.now();
}

@Entity @Table(name = "citations")
class CitationEntity {
    @Id UUID id;
    @Column(name = "agent_run_id", nullable = false) UUID agentRunId;
    @Column(name = "guideline_id", nullable = false) String guidelineId;
    @Column(name = "chunk_id", nullable = false) String chunkId;
    @Column(name = "claim_key", nullable = false) String claimKey;
    @Column(nullable = false) String title;
    @Column(name = "section_name", nullable = false) String section;
    @Column(name = "quote_text", nullable = false) String quote;
    @Column(name = "source_url") String sourceUrl;
    @Column(name = "license_note") String licenseNote;
}

@Entity @Table(name = "followup_plans")
class FollowupPlan {
    @Id UUID id;
    @Column(name = "visit_id", nullable = false) UUID visitId;
    @Enumerated(EnumType.STRING) @Column(nullable = false) PlanStatus status = PlanStatus.DRAFT;
    @Column(name = "template_code", nullable = false) String templateCode = "GENERAL_FOLLOWUP_V1";
    @Column(name = "owner_id", nullable = false) UUID ownerId;
    @Column(name = "activated_at") OffsetDateTime activatedAt;
    @Version long version;
    @Column(name = "created_at", nullable = false) OffsetDateTime createdAt = OffsetDateTime.now();
}

@Entity @Table(name = "patient_profiles")
class PatientProfile {
    @Id UUID id;
    @Column(name = "owner_id", nullable = false, unique = true) UUID ownerId;
    @JdbcTypeCode(SqlTypes.JSON) @Column(name = "profile_data", nullable = false, columnDefinition = "jsonb") String profileData = "{}";
    @Version long version;
    @Column(name = "updated_at", nullable = false) OffsetDateTime updatedAt = OffsetDateTime.now();
}

@Entity @Table(name = "visit_supplements")
class VisitSupplement {
    @Id UUID id;
    @Column(name = "visit_id", nullable = false) UUID visitId;
    @Column(name = "owner_id", nullable = false) UUID ownerId;
    @Column(nullable = false) String content;
    @Column(name = "created_at", nullable = false) OffsetDateTime createdAt = OffsetDateTime.now();
}

@Entity @Table(name = "followup_tasks")
class FollowupTask {
    @Id UUID id;
    @Column(name = "plan_id", nullable = false) UUID planId;
    @Column(name = "task_code", nullable = false) String taskCode;
    @Column(nullable = false) String title;
    @Column(name = "due_at", nullable = false) OffsetDateTime dueAt;
    @Enumerated(EnumType.STRING) @Column(nullable = false) TaskStatus status = TaskStatus.PENDING;
    @Column(name = "assignee_id") UUID assigneeId;
    @Column(name = "result_summary") String resultSummary;
    @Column(name = "completed_at") OffsetDateTime completedAt;
}

@Entity @Table(name = "safety_alerts")
class SafetyAlert {
    @Id UUID id;
    @Column(name = "run_id") String runId;
    @Column(name = "visit_id") UUID visitId;
    @Column(nullable = false) String category;
    @Column(nullable = false) String severity;
    @Column(name = "reason_codes", nullable = false) String reasonCodes;
    @Column(name = "redacted_summary", nullable = false) String redactedSummary;
    @Column(nullable = false) String status = "OPEN";
    @Column(name = "created_at", nullable = false) OffsetDateTime createdAt = OffsetDateTime.now();
}

@Entity @Table(name = "audit_logs")
class AuditLog {
    @Id UUID id;
    @Column(name = "actor_id") UUID actorId;
    @Column(nullable = false) String action;
    @Column(name = "target_type", nullable = false) String targetType;
    @Column(name = "target_id") UUID targetId;
    @Column(name = "request_id", nullable = false) String requestId;
    @Column(nullable = false) String result;
    @Column(name = "metadata_json", nullable = false) String metadataJson = "{}";
    @Column(name = "created_at", nullable = false) OffsetDateTime createdAt = OffsetDateTime.now();
}

