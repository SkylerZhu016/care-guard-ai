package com.example.medsim;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.*;

@Service
class PlatformService {
    private static final Set<String> VALID_TEMPLATES = Set.of("GENERAL_FOLLOWUP_V1");
    private final VisitRepository visits; private final SymptomRepository symptoms; private final TriageRepository triage;
    private final AgentRunRepository runs; private final CitationRepository citations; private final FollowupPlanRepository plans;
    private final FollowupTaskRepository tasks; private final SafetyAlertRepository alerts; private final AuditRepository audits;
    private final UserRepository users; private final RuleEngine rules; private final AiClient ai; private final ObjectMapper mapper;
    private final PrivacySanitizer privacy; private final KnowledgeService knowledge;

    PlatformService(VisitRepository visits, SymptomRepository symptoms, TriageRepository triage, AgentRunRepository runs,
                    CitationRepository citations, FollowupPlanRepository plans, FollowupTaskRepository tasks,
                    SafetyAlertRepository alerts, AuditRepository audits, UserRepository users, RuleEngine rules,
                    AiClient ai, ObjectMapper mapper, PrivacySanitizer privacy, KnowledgeService knowledge) {
        this.visits=visits; this.symptoms=symptoms; this.triage=triage; this.runs=runs; this.citations=citations;
        this.plans=plans; this.tasks=tasks; this.alerts=alerts; this.audits=audits; this.users=users; this.rules=rules; this.ai=ai; this.mapper=mapper;
        this.privacy=privacy; this.knowledge=knowledge;
    }

    @Transactional
    VisitView createVisit(AuthPrincipal actor, VisitInput input) {
        var visit = new Visit(); visit.id=UUID.randomUUID(); visit.ownerId=actor.id(); visit.chiefComplaint=privacy.sanitize(input.chiefComplaint()); visit.freeText=privacy.sanitize(input.freeText());
        visits.save(visit); replaceSymptoms(visit.id, input.symptoms()); audit(actor.id(), "VISIT_CREATED", "VISIT", visit.id, "SUCCESS", Map.of("fixture", true));
        return view(visit);
    }

    @Transactional
    VisitView updateIntake(AuthPrincipal actor, UUID visitId, VisitInput input) {
        var visit = ownedDraft(actor, visitId); visit.chiefComplaint=privacy.sanitize(input.chiefComplaint()); visit.freeText=privacy.sanitize(input.freeText());
        replaceSymptoms(visit.id, input.symptoms()); audit(actor.id(), "VISIT_INTAKE_UPDATED", "VISIT", visit.id, "SUCCESS", Map.of());
        return view(visit);
    }

    @Transactional
    VisitView submit(AuthPrincipal actor, UUID visitId, String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) throw new ApiException(HttpStatus.BAD_REQUEST, "IDEMPOTENCY_KEY_REQUIRED", "提交必须包含 Idempotency-Key");
        var existing = visits.findByIdempotencyKey(idempotencyKey);
        if (existing.isPresent()) {
            if (!existing.get().ownerId.equals(actor.id())) throw ApiException.forbidden();
            return view(existing.get());
        }
        var visit = ownedDraft(actor, visitId);
        var symptomEntities = symptoms.findByVisitId(visit.id);
        if (symptomEntities.isEmpty()) throw new ApiException(HttpStatus.BAD_REQUEST, "VISIT_SYMPTOMS_REQUIRED", "至少填写一项结构化症状");
        var inputs = symptomEntities.stream().map(this::symptomInput).toList();
        var outcome = rules.evaluate(inputs);
        transition(visit, VisitStatus.SUBMITTED); visit.idempotencyKey=idempotencyKey; visit.submittedAt=OffsetDateTime.now(); visits.save(visit);
        var result = new TriageResult(); result.id=UUID.randomUUID(); result.visitId=visit.id; result.ruleUrgency=outcome.urgency();
        result.ruleReasonCodes=String.join(",", outcome.reasonCodes()); result.coverageStatus=outcome.coverageStatus(); result.assessmentStatus=outcome.assessmentStatus(); triage.save(result);
        transition(visit, VisitStatus.PROCESSING); visits.save(visit);
        runAi(visit, symptomEntities, outcome, result, actor.id());
        transition(visit, VisitStatus.PENDING_REVIEW); visits.save(visit);
        var submitMetadata=new LinkedHashMap<String,Object>(); submitMetadata.put("ruleUrgency", outcome.urgency()==null?"MANUAL_REVIEW":outcome.urgency().name());
        submitMetadata.put("coverageStatus", outcome.coverageStatus().name());
        audit(actor.id(), "VISIT_SUBMITTED", "VISIT", visit.id, "SUCCESS", submitMetadata);
        return view(visit);
    }

    @Transactional(readOnly = true)
    List<VisitView> myVisits(AuthPrincipal actor) { return visits.findByOwnerIdOrderByCreatedAtDesc(actor.id()).stream().map(this::view).toList(); }

    @Transactional(readOnly = true)
    List<VisitView> clinicianQueue() { return visits.findByStatusOrderBySubmittedAtAsc(VisitStatus.PENDING_REVIEW).stream().map(this::view).toList(); }

    @Transactional(readOnly = true)
    VisitView getVisit(AuthPrincipal actor, UUID id) {
        var visit=visits.findById(id).orElseThrow(ApiException::notFound);
        if (actor.role()==Role.PATIENT && !visit.ownerId.equals(actor.id())) throw ApiException.notFound();
        if (actor.role()==Role.FOLLOWUP_STAFF) throw ApiException.forbidden();
        return view(visit);
    }

    @Transactional
    VisitView review(AuthPrincipal actor, UUID triageId, ReviewInput input) {
        var result=triage.findById(triageId).orElseThrow(ApiException::notFound);
        var visit=visits.findById(result.visitId).orElseThrow(ApiException::notFound);
        if (visit.status!=VisitStatus.PENDING_REVIEW) throw ApiException.conflict("VISIT_INVALID_TRANSITION", "病例不在待审核状态");
        Urgency requested=input.finalUrgency()==null ? (result.aiUrgency==null?result.ruleUrgency:result.aiUrgency) : input.finalUrgency();
        if (input.decision()!=ReviewDecision.REJECT && requested==null) throw new ApiException(HttpStatus.BAD_REQUEST,"TRIAGE_FINAL_URGENCY_REQUIRED","当前规则未自动分级，请由医务人员填写最终等级");
        if (input.decision()!=ReviewDecision.REJECT && result.ruleUrgency!=null && requested.ordinal()<result.ruleUrgency.ordinal()) throw ApiException.conflict("TRIAGE_RULE_DOWNGRADE_FORBIDDEN", "最终等级不能低于规则等级");
        result.finalUrgency=input.decision()==ReviewDecision.REJECT?null:requested; result.reviewDecision=input.decision(); result.reviewReason=privacy.sanitize(input.reason()); result.reviewerId=actor.id(); result.reviewedAt=OffsetDateTime.now();
        triage.save(result);
        if (input.decision()==ReviewDecision.REJECT) transition(visit, VisitStatus.REJECTED); else transition(visit, VisitStatus.REVIEWED);
        visits.save(visit);
        var metadata=new LinkedHashMap<String,Object>(); metadata.put("decision",input.decision().name()); if(result.finalUrgency!=null) metadata.put("finalUrgency",result.finalUrgency.name());
        audit(actor.id(), input.decision()==ReviewDecision.REJECT?"TRIAGE_REJECTED":"TRIAGE_REVIEWED", "TRIAGE_RESULT", result.id, "SUCCESS", metadata);
        return view(visit);
    }

    @Transactional
    PlanView createPlan(AuthPrincipal actor, PlanInput input) {
        var visit=visits.findById(input.visitId()).orElseThrow(ApiException::notFound);
        if (visit.status!=VisitStatus.REVIEWED) throw ApiException.conflict("FOLLOWUP_REQUIRES_REVIEW", "病例审核后才能创建随访计划");
        if (!VALID_TEMPLATES.contains(input.templateCode())) throw new ApiException(HttpStatus.BAD_REQUEST, "FOLLOWUP_TEMPLATE_UNKNOWN", "未知的随访计划模板");
        if (plans.existsByVisitId(visit.id)) throw ApiException.conflict("FOLLOWUP_PLAN_ALREADY_EXISTS", "该病例已经创建随访计划");
        var plan=new FollowupPlan(); plan.id=UUID.randomUUID(); plan.visitId=visit.id; plan.ownerId=visit.ownerId; plan.templateCode=input.templateCode();
        plans.save(plan); audit(actor.id(), "FOLLOWUP_PLAN_CREATED", "FOLLOWUP_PLAN", plan.id, "SUCCESS", Map.of("template", plan.templateCode));
        return planView(plan);
    }

    @Transactional
    PlanView activatePlan(AuthPrincipal actor, UUID planId) {
        var plan=plans.findById(planId).orElseThrow(ApiException::notFound);
        if (plan.status!=PlanStatus.DRAFT) throw ApiException.conflict("FOLLOWUP_INVALID_TRANSITION", "只有草稿计划可激活");
        var visit=visits.findById(plan.visitId).orElseThrow(ApiException::notFound);
        plan.status=PlanStatus.ACTIVE; plan.activatedAt=OffsetDateTime.now(); plans.save(plan);
        var assignee=users.findFirstByRole(Role.FOLLOWUP_STAFF).orElseThrow();
        createTask(plan, assignee.id, "BP_RECORD", "记录血压数据", 7);
        createTask(plan, assignee.id, "SYMPTOM_CHECK", "完成结构化症状复核", 7);
        createTask(plan, assignee.id, "ADHERENCE_CHECK", "记录随访计划执行情况", 14);
        createTask(plan, assignee.id, "CLINICIAN_REVIEW", "第 4 周医务人员复核", 28);
        transition(visit, VisitStatus.FOLLOWUP_ACTIVE); visits.save(visit);
        audit(actor.id(), "FOLLOWUP_PLAN_ACTIVATED", "FOLLOWUP_PLAN", plan.id, "SUCCESS", Map.of("taskCount", 4));
        return planView(plan);
    }

    @Transactional(readOnly = true)
    List<TaskView> assignedTasks(AuthPrincipal actor) {
        if (actor.role()==Role.PATIENT) {
            return plans.findByOwnerId(actor.id()).stream().flatMap(plan->tasks.findByPlanId(plan.id).stream()).map(this::taskView).toList();
        }
        return tasks.findByAssigneeIdOrderByDueAtAsc(actor.id()).stream().map(this::taskView).toList();
    }

    @Transactional
    TaskView updateTask(AuthPrincipal actor, UUID taskId, TaskUpdate input) {
        var task=tasks.findById(taskId).orElseThrow(ApiException::notFound);
        if (!Objects.equals(task.assigneeId, actor.id())) throw ApiException.forbidden();
        boolean start=task.status==TaskStatus.PENDING && input.status()==TaskStatus.IN_PROGRESS;
        boolean complete=task.status==TaskStatus.IN_PROGRESS && input.status()==TaskStatus.COMPLETED;
        if (!start && !complete) throw ApiException.conflict("FOLLOWUP_TASK_INVALID_TRANSITION", "任务只允许待执行→进行中→已完成");
        if (complete && (input.resultSummary()==null || input.resultSummary().isBlank())) throw new ApiException(HttpStatus.BAD_REQUEST,"FOLLOWUP_RESULT_REQUIRED","完成任务必须填写结果摘要");
        task.status=input.status(); task.resultSummary=privacy.sanitize(input.resultSummary()); if(task.status==TaskStatus.COMPLETED) task.completedAt=OffsetDateTime.now();
        tasks.save(task); audit(actor.id(), "FOLLOWUP_TASK_UPDATED", "FOLLOWUP_TASK", task.id, "SUCCESS", Map.of("status", task.status.name()));
        return taskView(task);
    }

    @Transactional(readOnly = true) List<AlertView> listAlerts() { return alerts.findAllByOrderByCreatedAtDesc().stream().map(this::alertView).toList(); }
    @Transactional(readOnly = true) List<AuditView> listAudits() { return audits.findTop100ByOrderByCreatedAtDesc().stream().map(this::auditView).toList(); }
    @Transactional(readOnly = true) List<AgentRunView> listRuns() { return runs.findAll().stream().sorted(Comparator.comparing((AgentRun r)->r.createdAt).reversed()).map(this::runView).toList(); }
    @Transactional(readOnly = true) List<GuidelineView> listGuidelines() { return knowledge.listGuidelines(); }
    @Transactional void reindexGuidelines(AuthPrincipal actor) { knowledge.reindexKnowledge(); audit(actor.id(),"KNOWLEDGE_REINDEXED","KNOWLEDGE_BASE",null,"SUCCESS",Map.of("source","official-web-corpus")); }
    @Transactional void activateGuideline(AuthPrincipal actor,String guidelineId,String versionId) { knowledge.activateVersion(guidelineId,versionId); audit(actor.id(),"KNOWLEDGE_VERSION_ACTIVATED","GUIDELINE_VERSION",null,"SUCCESS",Map.of("guidelineId",guidelineId,"versionId",versionId)); }

    private void runAi(Visit visit, List<SymptomEntity> symptomEntities, RuleOutcome outcome, TriageResult triageResult, UUID actor) {
        String runId="run-"+UUID.randomUUID(); var run=new AgentRun(); run.id=UUID.randomUUID(); run.runId=runId; run.visitId=visit.id; run.status="QUEUED"; runs.save(run);
        try {
            var job=ai.analyze(runId, visit, symptomEntities, outcome); run.status=job.status(); run.durationMs=job.durationMs();
            if(job.result()!=null) {
                var result=job.result();
                if (outcome.urgency()!=null && result.proposedUrgency()!=null && result.proposedUrgency().ordinal()<outcome.urgency().ordinal()) throw new IllegalStateException("AI_RULE_DOWNGRADE");
                if (result.citations()==null || result.citations().isEmpty() || result.citations().stream().anyMatch(c->!knowledge.isActiveChunk(c.chunkId()))) throw new IllegalStateException("AI_INVALID_CITATION");
                run.provider=result.provider(); run.modelName=result.model(); run.outputHash=result.outputHash(); run.safetyDecision=result.safety().decision();
                run.safetyReasonCodes=String.join(",", result.safety().reasonCodes());
                run.agentTrace=result.agentTrace()==null?"":String.join(",",result.agentTrace());
                if(result.versions()!=null){ run.promptVersion=result.versions().getOrDefault("prompt",run.promptVersion); run.knowledgeBaseVersion=result.versions().getOrDefault("knowledgeBase",run.knowledgeBaseVersion); run.ruleSetVersion=result.versions().getOrDefault("rules",run.ruleSetVersion); }
                triageResult.aiUrgency=rules.max(outcome.urgency(), result.proposedUrgency()); triageResult.aiSummary=result.caseSummary(); triage.save(triageResult);
                runs.save(run);
                for(var item:result.citations()){ var c=new CitationEntity(); c.id=UUID.randomUUID(); c.agentRunId=run.id; c.guidelineId=item.guidelineId(); c.chunkId=item.chunkId(); c.claimKey=item.claimKey(); c.title=item.title(); c.section=item.section(); c.quote=item.quote(); c.sourceUrl=item.sourceUrl(); c.licenseNote=item.licenseNote(); citations.save(c); }
                if(!"PASS".equals(result.safety().decision())) createAlert(run, visit, result.safety().reasonCodes(), "AI_SAFETY_BLOCK");
            } else { run.errorCode=job.errorCode(); runs.save(run); }
        } catch(Exception ex) {
            run.status="FAILED"; run.errorCode=ex.getMessage()==null?ex.getClass().getSimpleName():ex.getMessage().substring(0,Math.min(80,ex.getMessage().length())); runs.save(run);
            createAlert(run, visit, List.of(run.errorCode), "AI_UNAVAILABLE_OR_INVALID");
        }
    }

    private void createAlert(AgentRun run, Visit visit, List<String> reasons, String category) {
        var alert=new SafetyAlert(); alert.id=UUID.randomUUID(); alert.runId=run.runId; alert.visitId=visit.id; alert.category=category; alert.severity="HIGH";
        alert.reasonCodes=String.join(",", reasons); alert.redactedSummary="自动信息整理未完成，需要人工复核"; alerts.save(alert);
    }
    private void transition(Visit visit, VisitStatus target) { if(!rules.canTransition(visit.status,target)) throw ApiException.conflict("VISIT_INVALID_TRANSITION", "不允许的病例状态迁移"); visit.status=target; }
    private Visit ownedDraft(AuthPrincipal actor, UUID id) { var v=visits.findById(id).orElseThrow(ApiException::notFound); if(!v.ownerId.equals(actor.id())) throw ApiException.notFound(); if(v.status!=VisitStatus.DRAFT) throw ApiException.conflict("VISIT_NOT_EDITABLE", "只有草稿可编辑"); return v; }
    private void replaceSymptoms(UUID visitId, List<SymptomInput> input) { symptoms.deleteByVisitId(visitId); for(var item:input){ var s=new SymptomEntity(); s.id=UUID.randomUUID(); s.visitId=visitId; s.code=item.code(); s.name=privacy.sanitize(item.name()); s.legacySeverity=item.severity(); s.onset=privacy.sanitize(item.onset()); s.catalogVersion="legacy-v1"; s.supportLevel=Set.of("CHEST_PAIN","DYSPNEA","SYNCOPE","ALTERED_CONSCIOUSNESS").contains(item.code())?SupportLevel.RULE_SUPPORTED:SupportLevel.RECORD_ONLY; s.reportSource="LEGACY"; symptoms.save(s); } }
    private void createTask(FollowupPlan plan, UUID assignee, String code, String title, int dueDays) { var t=new FollowupTask(); t.id=UUID.randomUUID(); t.planId=plan.id; t.assigneeId=assignee; t.taskCode=code; t.title=title; t.dueAt=OffsetDateTime.now().plusDays(dueDays); tasks.save(t); }
    private List<String> split(String value) { return value==null||value.isBlank()?List.of():Arrays.stream(value.split(",")).filter(s->!s.isBlank()).toList(); }
    private SymptomInput symptomInput(SymptomEntity s){ return new SymptomInput(s.code,s.name,s.legacySeverity==null?0:s.legacySeverity,s.onset); }
    private VisitView view(Visit v){ var tr=triage.findByVisitId(v.id).map(this::triageView).orElse(null); return new VisitView(v.id,v.ownerId,v.status,v.chiefComplaint,v.freeText,symptoms.findByVisitId(v.id).stream().map(this::symptomInput).toList(),tr,runs.findByVisitIdOrderByCreatedAtDesc(v.id).stream().map(this::runView).toList(),v.createdAt,v.submittedAt); }
    private TriageView triageView(TriageResult t){ return new TriageView(t.id,t.ruleUrgency,t.aiUrgency,t.finalUrgency,split(t.ruleReasonCodes),t.aiSummary,t.reviewDecision,t.reviewReason); }
    private AgentRunView runView(AgentRun r){ return new AgentRunView(r.runId,r.status,r.provider,r.modelName,r.safetyDecision,split(r.safetyReasonCodes),split(r.agentTrace),r.durationMs,r.errorCode,citations.findByAgentRunId(r.id).stream().map(c->new CitationView(c.guidelineId,c.chunkId,c.claimKey,c.title,c.section,c.quote,c.sourceUrl,c.licenseNote)).toList()); }
    private PlanView planView(FollowupPlan p){ return new PlanView(p.id,p.visitId,p.status,p.templateCode,p.activatedAt,tasks.findByPlanId(p.id).stream().map(this::taskView).toList()); }
    private TaskView taskView(FollowupTask t){ return new TaskView(t.id,t.planId,t.taskCode,t.title,t.dueAt,t.status,t.resultSummary); }
    private AlertView alertView(SafetyAlert a){ return new AlertView(a.id,a.runId,a.visitId,a.category,a.severity,split(a.reasonCodes),a.redactedSummary,a.status,a.createdAt); }
    private AuditView auditView(AuditLog a){ return new AuditView(a.id,a.action,a.targetType,a.targetId,a.requestId,a.result,a.metadataJson,a.createdAt); }
    private void audit(UUID actor,String action,String type,UUID target,String result,Map<String,Object> metadata){ var a=new AuditLog(); a.id=UUID.randomUUID(); a.actorId=actor; a.action=action; a.targetType=type; a.targetId=target; a.requestId=UUID.randomUUID().toString(); a.result=result; try{a.metadataJson=mapper.writeValueAsString(metadata);}catch(JsonProcessingException ignored){a.metadataJson="{}";} audits.save(a); }
}

