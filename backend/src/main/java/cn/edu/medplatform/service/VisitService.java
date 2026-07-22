package cn.edu.medplatform.service;

import cn.edu.medplatform.entity.*;
import cn.edu.medplatform.repository.*;
import cn.edu.medplatform.common.*;
import cn.edu.medplatform.security.SecurityUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
public class VisitService {
    private final VisitRepository visitRepo;
    private final VisitStatusLogRepository logRepo;
    private final RuleDefinitionRepository ruleRepo;
    private final RuleHitRepository hitRepo;
    private final AgentRunRepository runRepo;
    private final RuleEngine ruleEngine;
    private final AuditService auditService;
    private final AiServiceClient aiClient;
    private final ObjectMapper om = new ObjectMapper();

    @Value("${app.ai-service-url}") private String aiServiceUrl;

    private static final Map<String, Set<String>> TRANSITIONS = new HashMap<>();
    static {
        TRANSITIONS.put("DRAFT", Set.of("SUBMITTED"));
        TRANSITIONS.put("SUBMITTED", Set.of("STRUCTURING", "FAILED"));
        TRANSITIONS.put("STRUCTURING", Set.of("RULE_SCREENED", "FAILED"));
        TRANSITIONS.put("RULE_SCREENED", Set.of("AI_ANALYZING", "FAILED"));
        TRANSITIONS.put("AI_ANALYZING", Set.of("PENDING_REVIEW", "FAILED"));
        TRANSITIONS.put("PENDING_REVIEW", Set.of("REVIEWED", "REJECTED", "NEED_INFO"));
        TRANSITIONS.put("NEED_INFO", Set.of("PENDING_REVIEW"));
        TRANSITIONS.put("REVIEWED", Set.of("ARCHIVED"));
        TRANSITIONS.put("FAILED", Set.of("SUBMITTED")); // retry
    }

    @Transactional
    public Map<String, Object> saveDraft(Long userId, Long patientId, Map<String, Object> formData) {
        Visit visit = new Visit();
        visit.setVisitNo("V" + System.currentTimeMillis());
        visit.setPatientId(patientId);
        visit.setOwnerUserId(userId);
        visit.setStatus("DRAFT");
        visit.setFormData(toJson(formData));
        visit.setCreatedBy(userId);
        visitRepo.save(visit);
        auditService.logCurrent("VISIT_DRAFT", "VISIT", String.valueOf(visit.getId()), "草稿保存");
        return Map.of("id", visit.getId(), "status", visit.getStatus());
    }

    @Transactional
    public Map<String, Object> submit(Long userId, Long patientId, Map<String, Object> formData, String idempotencyKey) {
        Visit visit = new Visit();
        visit.setVisitNo("V" + System.currentTimeMillis());
        visit.setPatientId(patientId);
        visit.setOwnerUserId(userId);
        visit.setStatus("SUBMITTED");
        visit.setFormData(toJson(formData));
        visit.setCreatedBy(userId);
        visit.setSubmittedAt(LocalDateTime.now());
        visitRepo.save(visit);
        logTransition(visit.getId(), null, "SUBMITTED", userId, "PATIENT", "患者提交");

        // 状态机 → STRUCTURING
        transition(visit, "STRUCTURING", userId, "系统", "开始结构化处理");

        // 执行规则引擎
        Map<String, Object> ctx = RuleEngine.buildContext(formData);
        List<RuleDefinition> rules = ruleRepo.findByEnabledTrueAndDeletedFalseOrderByPriorityAsc();
        List<Map<String, Object>> hits = new ArrayList<>();
        String maxRisk = "LOW";
        for (RuleDefinition rule : rules) {
            Map<String, Object> hit = ruleEngine.evaluate(rule, ctx);
            if (hit != null) {
                RuleHit rh = new RuleHit();
                rh.setVisitId(visit.getId());
                rh.setRuleId(rule.getId());
                rh.setRuleVersion(rule.getCurrentVersion());
                rh.setRuleCode(rule.getCode());
                rh.setRuleName(rule.getName());
                rh.setCategory(rule.getCategory());
                rh.setRiskLevel(rule.getRiskLevel());
                rh.setMessage(rule.getMessage());
                hitRepo.save(rh);
                hits.add(hit);
                maxRisk = maxRisk(maxRisk, rule.getRiskLevel());
            }
        }
        visit.setRiskLevel(maxRisk);
        visitRepo.save(visit);
        transition(visit, "RULE_SCREENED", userId, "系统", "规则预筛完成");

        // 创建 AgentRun 并启动 AI 流水线
        AgentRun run = new AgentRun();
        run.setVisitId(visit.getId());
        run.setStatus("PENDING");
        run.setTriggerType("SUBMIT");
        runRepo.save(run);

        transition(visit, "AI_ANALYZING", userId, "系统", "AI 分析中");

        // 异步调用 AI 服务
        try {
            aiClient.startPipeline(run.getId(), visit.getId());
        } catch (Exception e) {
            // AI 不可用，降级：保留规则结果，标记失败
            run.setStatus("FAILED");
            run.setErrorMessage("AI 服务不可用: " + e.getMessage());
            runRepo.save(run);
            transition(visit, "FAILED", userId, "系统", "AI 服务不可用");
        }

        auditService.logCurrent("VISIT_SUBMIT", "VISIT", String.valueOf(visit.getId()), "提交预问诊");
        return Map.of("id", visit.getId(), "status", visit.getStatus());
    }

    @Transactional
    public void onAiCompleted(Long runId, String runStatus) {
        AgentRun run = runRepo.findById(runId).orElseThrow();
        Visit visit = visitRepo.findById(run.getVisitId()).orElseThrow();
        if ("COMPLETED".equals(runStatus)) {
            run.setStatus("COMPLETED");
            run.setFinishedAt(LocalDateTime.now());
            runRepo.save(run);
            transition(visit, "PENDING_REVIEW", 0L, "系统", "AI 分析完成，待审核");
        } else if ("REVIEW_FAILED".equals(runStatus)) {
            run.setStatus("REVIEW_FAILED");
            run.setFinishedAt(LocalDateTime.now());
            runRepo.save(run);
            transition(visit, "PENDING_REVIEW", 0L, "系统", "安全审查未通过，转人工审核");
        } else {
            run.setStatus("FAILED");
            run.setFinishedAt(LocalDateTime.now());
            runRepo.save(run);
            transition(visit, "FAILED", 0L, "系统", "AI 流水线失败");
        }
    }

    @Transactional
    public void supplement(Long visitId, Map<String, Object> formData, Long userId) {
        Visit visit = visitRepo.findById(visitId).orElseThrow();
        if (!"NEED_INFO".equals(visit.getStatus()))
            throw new BusinessException(ErrorCode.STATE_CONFLICT, "当前状态不允许补充");
        visit.setFormData(toJson(formData));
        visitRepo.save(visit);
        transition(visit, "PENDING_REVIEW", userId, "PATIENT", "患者补充信息");

        // 重跑 SAFETY+SUMMARY（简化：重跑整个流水线）
        AgentRun run = new AgentRun();
        run.setVisitId(visitId);
        run.setStatus("PENDING");
        run.setTriggerType("SUPPLEMENT");
        runRepo.save(run);
        try { aiClient.startPipeline(run.getId(), visitId); }
        catch (Exception e) { /* 降级 */ }
    }

    public Map<String, Object> getDetail(Long visitId) {
        Visit visit = visitRepo.findById(visitId).orElseThrow();
        checkAccess(visit);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", visit.getId());
        result.put("visitNo", visit.getVisitNo());
        result.put("patientId", visit.getPatientId());
        result.put("status", visit.getStatus());
        result.put("riskLevel", visit.getRiskLevel());
        result.put("formData", fromJson(visit.getFormData()));
        result.put("submittedAt", visit.getSubmittedAt());
        result.put("createdAt", visit.getCreatedAt());
        result.put("statusLogs", logRepo.findByVisitIdOrderByCreatedAtAsc(visitId));
        return result;
    }

    public AgentRun getLatestRun(Long visitId) {
        List<AgentRun> runs = runRepo.findByVisitIdOrderByCreatedAtDesc(visitId);
        return runs.isEmpty() ? null : runs.get(0);
    }

    private void transition(Visit visit, String to, Long operatorId, String role, String reason) {
        String from = visit.getStatus();
        if (!TRANSITIONS.getOrDefault(from, Set.of()).contains(to))
            throw new BusinessException(ErrorCode.STATE_CONFLICT, "非法状态转换: " + from + " → " + to);
        visit.setStatus(to);
        visitRepo.save(visit);
        logTransition(visit.getId(), from, to, operatorId, role, reason);
    }

    private void logTransition(Long visitId, String from, String to, Long operatorId, String role, String reason) {
        VisitStatusLog log = new VisitStatusLog();
        log.setVisitId(visitId);
        log.setFromStatus(from);
        log.setToStatus(to);
        log.setOperatorId(operatorId);
        log.setOperatorRole(role);
        log.setReason(reason);
        logRepo.save(log);
    }

    private void checkAccess(Visit visit) {
        if (SecurityUtils.isAdmin()) return;
        if (SecurityUtils.hasRole("DOCTOR") || SecurityUtils.hasRole("FOLLOWUP")) return;
        // 患者只能看自己的
        if (!visit.getOwnerUserId().equals(SecurityUtils.getCurrentUserId()))
            throw new BusinessException(ErrorCode.DATA_FORBIDDEN);
    }

    private String toJson(Object obj) {
        try { return om.writeValueAsString(obj); } catch (Exception e) { return "{}"; }
    }
    private Object fromJson(String json) {
        try { return om.readValue(json, Object.class); } catch (Exception e) { return Map.of(); }
    }
    private String maxRisk(String a, String b) {
        Map<String, Integer> order = Map.of("LOW", 0, "MEDIUM", 1, "HIGH", 2, "CRITICAL", 3);
        return order.getOrDefault(a, 0) >= order.getOrDefault(b, 0) ? a : b;
    }
}
