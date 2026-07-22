package com.example.medsim;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.*;

@Service
class IntakeV2Service {
    private final VisitRepository visits;
    private final SymptomRepository symptoms;
    private final TriageRepository triage;
    private final PatientProfileRepository profiles;
    private final VisitSupplementRepository supplements;
    private final AgentRunRepository runs;
    private final CitationRepository citations;
    private final SafetyAlertRepository alerts;
    private final AuditRepository audits;
    private final IntakeCatalog catalog;
    private final RuleEngine rules;
    private final AiClient ai;
    private final KnowledgeService knowledge;
    private final PrivacySanitizer privacy;
    private final ObjectMapper mapper;

    IntakeV2Service(VisitRepository visits, SymptomRepository symptoms, TriageRepository triage,
                    PatientProfileRepository profiles, VisitSupplementRepository supplements,
                    AgentRunRepository runs, CitationRepository citations, SafetyAlertRepository alerts,
                    AuditRepository audits, IntakeCatalog catalog, RuleEngine rules, AiClient ai,
                    KnowledgeService knowledge, PrivacySanitizer privacy, ObjectMapper mapper) {
        this.visits = visits; this.symptoms = symptoms; this.triage = triage; this.profiles = profiles;
        this.supplements = supplements; this.runs = runs; this.citations = citations; this.alerts = alerts;
        this.audits = audits; this.catalog = catalog; this.rules = rules; this.ai = ai;
        this.knowledge = knowledge; this.privacy = privacy; this.mapper = mapper;
    }

    IntakeCatalogView catalog() { return catalog.view(); }

    @Transactional(readOnly = true)
    PatientProfileView profile(AuthPrincipal actor) {
        return profiles.findByOwnerId(actor.id()).map(this::profileView)
            .orElse(new PatientProfileView(null, actor.id(), 0, emptyProfile(), null));
    }

    @Transactional
    PatientProfileView saveProfile(AuthPrincipal actor, PatientProfileInput input) {
        var value = profiles.findByOwnerId(actor.id()).orElseGet(() -> {
            var created = new PatientProfile(); created.id = UUID.randomUUID(); created.ownerId = actor.id(); return created;
        });
        value.profileData = json(sanitizeProfile(input));
        value.updatedAt = OffsetDateTime.now();
        profiles.save(value);
        audit(actor.id(), "PATIENT_PROFILE_UPDATED", "PATIENT_PROFILE", value.id, Map.of("version", value.version + 1));
        return profileView(value);
    }

    @Transactional
    VisitViewV2 create(AuthPrincipal actor, VisitIntakeV2Input input) {
        var visit = new Visit(); visit.id = UUID.randomUUID(); visit.ownerId = actor.id(); visit.intakeVersion = "INTAKE_V2";
        apply(visit, input); visits.save(visit); replaceReports(visit.id, input.symptomReports());
        audit(actor.id(), "VISIT_V2_CREATED", "VISIT", visit.id, Map.of("fixture", false));
        return view(visit);
    }

    @Transactional
    VisitViewV2 update(AuthPrincipal actor, UUID id, VisitIntakeV2Input input) {
        var visit = ownedDraft(actor, id);
        if (!"INTAKE_V2".equals(visit.intakeVersion)) throw ApiException.conflict("VISIT_LEGACY_READ_ONLY", "历史问诊只能查看，不能改写");
        apply(visit, input); replaceReports(visit.id, input.symptomReports()); visits.save(visit);
        audit(actor.id(), "VISIT_V2_AUTOSAVED", "VISIT", visit.id, Map.of());
        return view(visit);
    }

    @Transactional
    VisitViewV2 submit(AuthPrincipal actor, UUID id, String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank())
            throw new ApiException(HttpStatus.BAD_REQUEST, "IDEMPOTENCY_KEY_REQUIRED", "提交必须包含 Idempotency-Key");
        var existing = visits.findByIdempotencyKey(idempotencyKey);
        if (existing.isPresent()) {
            if (!existing.get().ownerId.equals(actor.id())) throw ApiException.forbidden();
            return view(existing.get());
        }
        var visit = ownedDraft(actor, id);
        if (!"INTAKE_V2".equals(visit.intakeVersion)) throw ApiException.conflict("VISIT_LEGACY_READ_ONLY", "历史问诊不能通过新版接口重新提交");
        var reports = symptoms.findByVisitId(visit.id);
        if (reports.isEmpty()) throw new ApiException(HttpStatus.BAD_REQUEST, "VISIT_SYMPTOMS_REQUIRED", "至少填写一项不适");
        var currentProfile = profiles.findByOwnerId(actor.id()).map(value -> readProfile(value.profileData)).orElse(emptyProfile());
        visit.profileSnapshot = json(currentProfile);
        visit.status = VisitStatus.SUBMITTED; visit.idempotencyKey = idempotencyKey; visit.submittedAt = OffsetDateTime.now(); visits.save(visit);
        var outcome = rules.evaluateV2(reports);
        var result = new TriageResult(); result.id = UUID.randomUUID(); result.visitId = visit.id;
        result.ruleUrgency = outcome.urgency(); result.ruleReasonCodes = String.join(",", outcome.reasonCodes());
        result.coverageStatus = outcome.coverageStatus(); result.assessmentStatus = outcome.assessmentStatus(); triage.save(result);
        visit.status = VisitStatus.PROCESSING; visits.save(visit);
        runAi(visit, reports, outcome, result);
        visit.status = VisitStatus.PENDING_REVIEW; visits.save(visit);
        var metadata = new LinkedHashMap<String,Object>(); metadata.put("coverageStatus", outcome.coverageStatus().name());
        metadata.put("assessmentStatus", outcome.assessmentStatus().name());
        if (outcome.urgency() != null) metadata.put("ruleUrgency", outcome.urgency().name());
        audit(actor.id(), "VISIT_V2_SUBMITTED", "VISIT", visit.id, metadata);
        return view(visit);
    }

    @Transactional(readOnly = true)
    List<VisitViewV2> mine(AuthPrincipal actor) {
        return visits.findByOwnerIdOrderByCreatedAtDesc(actor.id()).stream().map(this::view).toList();
    }

    @Transactional(readOnly = true)
    List<VisitViewV2> clinicianQueue() {
        return visits.findByStatusOrderBySubmittedAtAsc(VisitStatus.PENDING_REVIEW).stream().map(this::view).toList();
    }

    @Transactional(readOnly = true)
    VisitViewV2 get(AuthPrincipal actor, UUID id) {
        var visit = visits.findById(id).orElseThrow(ApiException::notFound);
        if (actor.role() == Role.PATIENT && !visit.ownerId.equals(actor.id())) throw ApiException.notFound();
        if (actor.role() == Role.FOLLOWUP_STAFF) throw ApiException.forbidden();
        return view(visit);
    }

    @Transactional
    VisitSupplementView supplement(AuthPrincipal actor, UUID id, VisitSupplementInput input) {
        var visit = visits.findById(id).orElseThrow(ApiException::notFound);
        if (!visit.ownerId.equals(actor.id())) throw ApiException.notFound();
        if (visit.status == VisitStatus.DRAFT) throw ApiException.conflict("VISIT_NOT_SUBMITTED", "草稿可直接编辑，无需补充信息");
        var value = new VisitSupplement(); value.id = UUID.randomUUID(); value.visitId = id; value.ownerId = actor.id();
        value.content = privacy.sanitize(input.content()); supplements.save(value);
        audit(actor.id(), "VISIT_SUPPLEMENT_ADDED", "VISIT", id, Map.of());
        return supplementView(value);
    }

    private void apply(Visit visit, VisitIntakeV2Input input) {
        catalog.require(input.primarySymptomCode());
        visit.primarySymptomCode = input.primarySymptomCode();
        visit.chiefComplaint = privacy.sanitize(input.chiefComplaint());
        visit.freeText = privacy.sanitize(input.freeText());
    }

    private void replaceReports(UUID visitId, List<SymptomReportInput> input) {
        symptoms.deleteByVisitId(visitId);
        var normalized = new ArrayList<>(input);
        addRelated(normalized, "chest.dyspnea", "DYSPNEA");
        addRelated(normalized, "chest.syncope", "SYNCOPE");
        addRelated(normalized, "syncope.chest_pain", "CHEST_PAIN");
        addRelated(normalized, "syncope.dyspnea", "DYSPNEA");
        for (var item : normalized) {
            var definition = catalog.require(item.symptomCode());
            if (definition.supportLevel() == SupportLevel.CUSTOM && (item.customName() == null || item.customName().isBlank()))
                throw new ApiException(HttpStatus.BAD_REQUEST, "CUSTOM_SYMPTOM_NAME_REQUIRED", "请填写其他不适的简短名称");
            var value = new SymptomEntity(); value.id = UUID.randomUUID(); value.visitId = visitId;
            value.codeSystem = "LOCAL_SYMPTOM_V2"; value.code = item.symptomCode();
            value.name = privacy.sanitize(definition.supportLevel() == SupportLevel.CUSTOM ? item.customName() : definition.name());
            value.catalogVersion = IntakeCatalog.VERSION; value.supportLevel = definition.supportLevel();
            value.reportSource = item.source() == null ? "CATALOG" : item.source(); value.onsetRange = item.onsetRange();
            value.course = item.course(); value.currentStatus = item.currentStatus(); value.activityImpact = item.activityImpact();
            value.answersJson = json(sanitizeAnswers(item.answers())); symptoms.save(value);
        }
    }

    private void addRelated(List<SymptomReportInput> reports, String questionId, String symptomCode) {
        boolean yes = reports.stream().flatMap(value -> safe(value.answers()).stream())
            .anyMatch(answer -> questionId.equals(answer.questionId()) && safe(answer.selectedOptions()).contains("YES"));
        if (yes && reports.stream().noneMatch(value -> symptomCode.equals(value.symptomCode())))
            reports.add(new SymptomReportInput(symptomCode, null, "RELATED_ANSWER", "UNKNOWN", "UNKNOWN", "PRESENT", "UNKNOWN", List.of()));
    }

    private List<QuestionAnswerInput> sanitizeAnswers(List<QuestionAnswerInput> answers) {
        return safe(answers).stream().map(value -> new QuestionAnswerInput(value.questionId(), safe(value.selectedOptions()), privacy.sanitize(value.supplementalText()))).toList();
    }

    private PatientProfileInput sanitizeProfile(PatientProfileInput value) {
        return new PatientProfileInput(value.ageBand(), value.physiologicalInfoStatus(), privacy.sanitize(value.physiologicalInfo()),
            value.chronicConditionsStatus(), sanitizeList(value.chronicConditions()), value.allergiesStatus(), sanitizeList(value.allergies()),
            value.longTermMedicationsStatus(), sanitizeList(value.longTermMedications()));
    }

    private List<String> sanitizeList(List<String> values) { return safe(values).stream().map(privacy::sanitize).filter(Objects::nonNull).toList(); }
    private <T> List<T> safe(List<T> values) { return values == null ? List.of() : values; }

    private void runAi(Visit visit, List<SymptomEntity> reports, RuleOutcome outcome, TriageResult triageResult) {
        String runId = "run-" + UUID.randomUUID(); var run = new AgentRun(); run.id = UUID.randomUUID(); run.runId = runId;
        run.visitId = visit.id; run.status = "QUEUED"; run.promptVersion = "intake-summary-v2"; run.ruleSetVersion = "fact-rules-v2"; runs.save(run);
        try {
            var job = ai.analyze(runId, visit, reports, outcome); run.status = job.status(); run.durationMs = job.durationMs();
            if (job.result() != null) {
                var result = job.result();
                if (outcome.urgency() != null && result.proposedUrgency() != null && result.proposedUrgency().ordinal() < outcome.urgency().ordinal())
                    throw new IllegalStateException("AI_RULE_DOWNGRADE");
                if (result.citations() != null && result.citations().stream().anyMatch(c -> !knowledge.isActiveChunk(c.chunkId())))
                    throw new IllegalStateException("AI_INVALID_CITATION");
                run.provider = result.provider(); run.modelName = result.model(); run.outputHash = result.outputHash();
                run.safetyDecision = result.safety().decision(); run.safetyReasonCodes = String.join(",", result.safety().reasonCodes());
                run.agentTrace = result.agentTrace() == null ? "" : String.join(",", result.agentTrace());
                triageResult.aiUrgency = rules.max(outcome.urgency(), result.proposedUrgency()); triageResult.aiSummary = result.caseSummary(); triage.save(triageResult);
                runs.save(run);
                for (var item : safe(result.citations())) {
                    var citation = new CitationEntity(); citation.id = UUID.randomUUID(); citation.agentRunId = run.id;
                    citation.guidelineId = item.guidelineId(); citation.chunkId = item.chunkId(); citation.claimKey = item.claimKey();
                    citation.title = item.title(); citation.section = item.section(); citation.quote = item.quote();
                    citation.sourceUrl = item.sourceUrl(); citation.licenseNote = item.licenseNote(); citations.save(citation);
                }
                if (!"PASS".equals(result.safety().decision())) createAlert(run, visit, result.safety().reasonCodes(), "AI_SAFETY_BLOCK");
            } else { run.errorCode = job.errorCode(); runs.save(run); }
        } catch (Exception exception) {
            run.status = "FAILED"; String message = exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage();
            run.errorCode = message.substring(0, Math.min(80, message.length())); runs.save(run);
            createAlert(run, visit, List.of(run.errorCode), "AI_UNAVAILABLE_OR_INVALID");
        }
    }

    private void createAlert(AgentRun run, Visit visit, List<String> reasons, String category) {
        var alert = new SafetyAlert(); alert.id = UUID.randomUUID(); alert.runId = run.runId; alert.visitId = visit.id;
        alert.category = category; alert.severity = "HIGH"; alert.reasonCodes = String.join(",", reasons);
        alert.redactedSummary = "自动信息整理未完成，需要人工复核"; alerts.save(alert);
    }

    private Visit ownedDraft(AuthPrincipal actor, UUID id) {
        var value = visits.findById(id).orElseThrow(ApiException::notFound);
        if (!value.ownerId.equals(actor.id())) throw ApiException.notFound();
        if (value.status != VisitStatus.DRAFT) throw ApiException.conflict("VISIT_NOT_EDITABLE", "提交后不能直接修改，可追加补充信息");
        return value;
    }

    private VisitViewV2 view(Visit value) {
        var reportViews = symptoms.findByVisitId(value.id).stream().map(this::reportView).toList();
        var snapshot = value.profileSnapshot == null ? null : readProfile(value.profileSnapshot);
        var triageView = triage.findByVisitId(value.id).map(this::triageView).orElse(null);
        return new VisitViewV2(value.id, value.ownerId, value.status, value.intakeVersion,
            value.primarySymptomCode == null && !reportViews.isEmpty() ? reportViews.get(0).symptomCode() : value.primarySymptomCode,
            value.chiefComplaint, value.freeText, reportViews, snapshot, triageView,
            runs.findByVisitIdOrderByCreatedAtDesc(value.id).stream().map(this::runView).toList(),
            supplements.findByVisitIdOrderByCreatedAtAsc(value.id).stream().map(this::supplementView).toList(), value.createdAt, value.submittedAt);
    }

    private SymptomReportView reportView(SymptomEntity value) {
        return new SymptomReportView(value.id, value.code, value.name, value.reportSource, value.catalogVersion, value.supportLevel,
            value.onsetRange, value.course, value.currentStatus, value.activityImpact, readAnswers(value.answersJson), value.legacySeverity, value.onset);
    }

    private TriageViewV2 triageView(TriageResult value) {
        return new TriageViewV2(value.id, value.ruleUrgency, value.aiUrgency, value.finalUrgency,
            value.coverageStatus, value.assessmentStatus, split(value.ruleReasonCodes), value.aiSummary,
            value.reviewDecision, value.reviewReason);
    }

    private PatientProfileView profileView(PatientProfile value) { return new PatientProfileView(value.id, value.ownerId, value.version, readProfile(value.profileData), value.updatedAt); }
    private VisitSupplementView supplementView(VisitSupplement value) { return new VisitSupplementView(value.id, value.content, value.createdAt); }
    private AgentRunView runView(AgentRun value) {
        return new AgentRunView(value.runId, value.status, value.provider, value.modelName, value.safetyDecision,
            split(value.safetyReasonCodes), split(value.agentTrace), value.durationMs, value.errorCode,
            citations.findByAgentRunId(value.id).stream().map(c -> new CitationView(c.guidelineId,c.chunkId,c.claimKey,c.title,c.section,c.quote,c.sourceUrl,c.licenseNote)).toList());
    }

    private String json(Object value) {
        try { return mapper.writeValueAsString(value); }
        catch (JsonProcessingException exception) { throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_STRUCTURED_DATA", "结构化信息无法保存"); }
    }

    private PatientProfileInput readProfile(String value) {
        try { return mapper.readValue(value, PatientProfileInput.class); }
        catch (JsonProcessingException exception) { return emptyProfile(); }
    }

    private List<QuestionAnswerInput> readAnswers(String value) {
        if (value == null || value.isBlank()) return List.of();
        try { return mapper.readValue(value, new TypeReference<>() {}); }
        catch (JsonProcessingException exception) { return List.of(); }
    }

    private PatientProfileInput emptyProfile() {
        return new PatientProfileInput("UNKNOWN", "UNKNOWN", "", "UNKNOWN", List.of(), "UNKNOWN", List.of(), "UNKNOWN", List.of());
    }

    private List<String> split(String value) { return value == null || value.isBlank() ? List.of() : Arrays.stream(value.split(",")).filter(v -> !v.isBlank()).toList(); }
    private void audit(UUID actor, String action, String type, UUID target, Map<String,Object> metadata) {
        var value = new AuditLog(); value.id = UUID.randomUUID(); value.actorId = actor; value.action = action; value.targetType = type;
        value.targetId = target; value.requestId = UUID.randomUUID().toString(); value.result = "SUCCESS"; value.metadataJson = json(metadata); audits.save(value);
    }
}
