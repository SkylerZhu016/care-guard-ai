package cn.edu.medplatform.controller;

import cn.edu.medplatform.entity.*;
import cn.edu.medplatform.repository.*;
import cn.edu.medplatform.service.*;
import cn.edu.medplatform.security.SecurityUtils;
import cn.edu.medplatform.common.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import com.fasterxml.jackson.databind.ObjectMapper;

// ============ 患者档案 ============
@RestController
@RequestMapping("/api/v1/patients")
@RequiredArgsConstructor
class PatientController {
    private final SimulatedPatientRepository patientRepo;
    private final PatientHistoryRepository historyRepo;
    private final AllergyRecordRepository allergyRepo;
    private final MedicationRecordRepository medRepo;
    private final UserRoleService userRoleService;

    @GetMapping
    public ResponseEntity<?> list(@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "10") int size,
                                   @RequestParam(required = false) String keyword) {
        Pageable p = PageRequest.of(page, size, Sort.by("createdAt").descending());
        org.springframework.data.domain.Page<SimulatedPatient> pg;
        if (SecurityUtils.hasRole("PATIENT") && !SecurityUtils.isAdmin())
            pg = patientRepo.findByOwnerUserId(SecurityUtils.getCurrentUserId(), p);
        else
            pg = patientRepo.search(keyword, p);
        // 把每条实体的慢性病标签解析成 List 放到 Map，再包成新 Page
        List<Map<String, Object>> mapped = new ArrayList<>();
        for (SimulatedPatient pt : pg) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", pt.getId());
            m.put("patientNo", pt.getPatientNo());
            m.put("ownerUserId", pt.getOwnerUserId());
            m.put("name", pt.getName());
            m.put("gender", pt.getGender());
            m.put("birthDate", pt.getBirthDate());
            m.put("phone", pt.getPhone());
            m.put("idCard", pt.getIdCard());
            m.put("bloodType", pt.getBloodType());
            m.put("chronicTags", parseJsonArray(pt.getChronicTags()));
            m.put("address", pt.getAddress());
            m.put("createdAt", pt.getCreatedAt());
            m.put("updatedAt", pt.getUpdatedAt());
            mapped.add(m);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("content", mapped);
        result.put("totalElements", pg.getTotalElements());
        result.put("totalPages", pg.getTotalPages());
        result.put("number", pg.getNumber());
        result.put("size", pg.getSize());
        return ResponseEntity.ok(result);
    }

    @PostMapping
    public ResponseEntity<?> create(@RequestBody Map<String, Object> body) {
        SimulatedPatient pt = new SimulatedPatient();
        pt.setPatientNo("SP" + System.currentTimeMillis());
        pt.setOwnerUserId(SecurityUtils.getCurrentUserId());
        pt.setName((String) body.get("name"));
        pt.setGender((String) body.get("gender"));
        if (body.get("birthDate") != null) pt.setBirthDate(LocalDate.parse(body.get("birthDate").toString()));
        pt.setPhone((String) body.get("phone"));
        pt.setIdCard((String) body.get("idCard"));
        pt.setBloodType((String) body.get("bloodType"));
        pt.setChronicTags(serializeJsonField(body.get("chronicTags")));
        pt.setAddress((String) body.get("address"));
        patientRepo.save(pt);
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("id", pt.getId());
        resp.put("patientNo", pt.getPatientNo());
        resp.put("ownerUserId", pt.getOwnerUserId());
        resp.put("name", pt.getName());
        resp.put("gender", pt.getGender());
        resp.put("birthDate", pt.getBirthDate());
        resp.put("phone", pt.getPhone());
        resp.put("idCard", pt.getIdCard());
        resp.put("bloodType", pt.getBloodType());
        resp.put("chronicTags", parseJsonArray(pt.getChronicTags()));
        resp.put("address", pt.getAddress());
        resp.put("createdAt", pt.getCreatedAt());
        resp.put("updatedAt", pt.getUpdatedAt());
        return ResponseEntity.ok(resp);
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> detail(@PathVariable Long id) {
        SimulatedPatient pt = patientRepo.findById(id).orElseThrow();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", pt.getId());
        result.put("patientNo", pt.getPatientNo());
        result.put("name", pt.getName());
        result.put("gender", pt.getGender());
        result.put("birthDate", pt.getBirthDate());
        result.put("phone", pt.getPhone());
        result.put("bloodType", pt.getBloodType());
        result.put("chronicTags", parseJsonArray(pt.getChronicTags()));
        result.put("address", pt.getAddress());
        result.put("histories", historyRepo.findByPatientId(id));
        result.put("allergies", allergyRepo.findByPatientId(id));
        result.put("medications", medRepo.findByPatientId(id));
        return ResponseEntity.ok(result);
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> update(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        SimulatedPatient pt = patientRepo.findById(id).orElseThrow();
        if (body.get("name") != null) pt.setName((String) body.get("name"));
        if (body.get("gender") != null) pt.setGender((String) body.get("gender"));
        if (body.get("birthDate") != null) pt.setBirthDate(LocalDate.parse(body.get("birthDate").toString()));
        if (body.get("phone") != null) pt.setPhone((String) body.get("phone"));
        if (body.get("idCard") != null) pt.setIdCard((String) body.get("idCard"));
        if (body.get("bloodType") != null) pt.setBloodType((String) body.get("bloodType"));
        if (body.get("chronicTags") != null) pt.setChronicTags(serializeJsonField(body.get("chronicTags")));
        if (body.get("address") != null) pt.setAddress((String) body.get("address"));
        patientRepo.save(pt);
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("id", pt.getId());
        resp.put("patientNo", pt.getPatientNo());
        resp.put("ownerUserId", pt.getOwnerUserId());
        resp.put("name", pt.getName());
        resp.put("gender", pt.getGender());
        resp.put("birthDate", pt.getBirthDate());
        resp.put("phone", pt.getPhone());
        resp.put("idCard", pt.getIdCard());
        resp.put("bloodType", pt.getBloodType());
        resp.put("chronicTags", parseJsonArray(pt.getChronicTags()));
        resp.put("address", pt.getAddress());
        resp.put("createdAt", pt.getCreatedAt());
        resp.put("updatedAt", pt.getUpdatedAt());
        return ResponseEntity.ok(resp);
    }

    @PostMapping("/{id}/histories")
    public ResponseEntity<?> addHistory(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        PatientHistory h = new PatientHistory();
        h.setPatientId(id);
        h.setDiseaseName((String) body.get("diseaseName"));
        if (body.get("diagnosedAt") != null) h.setDiagnosedAt(LocalDate.parse(body.get("diagnosedAt").toString()));
        h.setNote((String) body.get("note"));
        historyRepo.save(h);
        return ResponseEntity.ok(h);
    }

    @PostMapping("/{id}/allergies")
    public ResponseEntity<?> addAllergy(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        AllergyRecord a = new AllergyRecord();
        a.setPatientId(id);
        a.setAllergen((String) body.get("allergen"));
        a.setReaction((String) body.get("reaction"));
        a.setSeverity((String) body.get("severity"));
        allergyRepo.save(a);
        return ResponseEntity.ok(a);
    }

    @PostMapping("/{id}/medications")
    public ResponseEntity<?> addMedication(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        MedicationRecord m = new MedicationRecord();
        m.setPatientId(id);
        m.setDrugName((String) body.get("drugName"));
        m.setDosage((String) body.get("dosage"));
        m.setFrequency((String) body.get("frequency"));
        if (body.get("startDate") != null) m.setStartDate(LocalDate.parse(body.get("startDate").toString()));
        medRepo.save(m);
        return ResponseEntity.ok(m);
    }

    @DeleteMapping("/{id}/{kind}/{subId}")
    public ResponseEntity<?> removeSub(@PathVariable Long id, @PathVariable String kind, @PathVariable Long subId) {
        switch (kind) {
            case "histories" -> historyRepo.deleteById(subId);
            case "allergies" -> allergyRepo.deleteById(subId);
            case "medications" -> medRepo.deleteById(subId);
        }
        return ResponseEntity.noContent().build();
    }

    /** 将 Object 序列化为 JSON 字符串（兼容 List → JSON 数组） */
    public static String serializeJsonField(Object val) {
        if (val == null) return "[]";
        if (val instanceof String s) return s;
        try {
            return new ObjectMapper().writeValueAsString(val);
        } catch (Exception e) {
            return val.toString();
        }
    }

    /** 把 JSON 字符串解析为 List 返回给前端 */
    public static List<?> parseJsonArray(String json) {
        if (json == null || json.isBlank()) return List.of();
        try {
            return new ObjectMapper().readValue(json, List.class);
        } catch (Exception e) {
            return List.of();
        }
    }

    /** 给单条 SimulatedPatient 注入 chronicTags (List) */
    private void parsePatientJsonFields(SimulatedPatient pt) {
        // 通过反射或 getter 暂时不可用，简单方式：把 chronicTags 转 List 后放回实体
        // 这里采用返回新 Map 的方式：list 已使用 Page 自身，再另开一个 detail 接口
    }
}

// ============ 审核 ============
@RestController
@RequestMapping("/api/v1/reviews")
@RequiredArgsConstructor
class ReviewController {
    private final VisitRepository visitRepo;
    private final ReviewRecordRepository reviewRepo;
    private final TriageResultRepository triageRepo;
    private final AuditService auditService;

    @GetMapping("/queue")
    @PreAuthorize("hasRole('DOCTOR')")
    public ResponseEntity<?> queue(@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "10") int size,
                                    @RequestParam(required = false) String riskLevel, @RequestParam(required = false) String status) {
        Pageable p = PageRequest.of(page, size);
        return ResponseEntity.ok(visitRepo.reviewQueue(riskLevel, status, p));
    }

    @PostMapping
    @PreAuthorize("hasRole('DOCTOR')")
    public ResponseEntity<?> submit(@RequestBody Map<String, Object> body) {
        Long visitId = Long.valueOf(body.get("visitId").toString());
        String action = (String) body.get("action");
        Visit visit = visitRepo.findById(visitId).orElseThrow();

        ReviewRecord rec = new ReviewRecord();
        rec.setVisitId(visitId);
        rec.setReviewerId(SecurityUtils.getCurrentUserId());
        rec.setAction(action);
        rec.setComment((String) body.get("comment"));

        switch (action) {
            case "APPROVE" -> visit.setStatus("REVIEWED");
            case "REJECT" -> visit.setStatus("REJECTED");
            case "REQUEST_INFO" -> visit.setStatus("NEED_INFO");
        }
        if (body.get("modifiedRiskLevel") != null) visit.setRiskLevel((String) body.get("modifiedRiskLevel"));
        visitRepo.save(visit);
        reviewRepo.save(rec);
        auditService.logCurrent("REVIEW_" + action, "VISIT", String.valueOf(visitId), action);
        return ResponseEntity.ok(rec);
    }

    @GetMapping("/visits/{visitId}")
    public ResponseEntity<?> history(@PathVariable Long visitId) {
        return ResponseEntity.ok(reviewRepo.findByVisitIdOrderByCreatedAtAsc(visitId));
    }
}

// ============ 随访 ============
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
class FollowupController {
    private final FollowupPlanRepository planRepo;
    private final FollowupTaskRepository taskRepo;
    private final FollowupRecordRepository recordRepo;
    private final SimulatedPatientRepository patientRepo;
    private final AuditService auditService;

    @PostMapping("/followup-plans")
    @PreAuthorize("hasRole('DOCTOR')")
    public ResponseEntity<?> createPlan(@RequestBody Map<String, Object> body) {
        FollowupPlan plan = new FollowupPlan();
        plan.setPatientId(Long.valueOf(body.get("patientId").toString()));
        if (body.get("visitId") != null) plan.setVisitId(Long.valueOf(body.get("visitId").toString()));
        plan.setCreatedBy(SecurityUtils.getCurrentUserId());
        plan.setPlanName((String) body.get("planName"));
        plan.setIntervalDays(body.get("intervalDays") != null ? (Integer) body.get("intervalDays") : 7);
        if (body.get("startDate") != null) plan.setStartDate(LocalDate.parse(body.get("startDate").toString()));
        plan.setEndCondition((String) body.get("endCondition"));
        plan.setItems(PatientController.serializeJsonField(body.get("items")));
        plan.setStatus("PENDING_START");
        planRepo.save(plan);
        auditService.logCurrent("FOLLOWUP_PLAN_CREATE", "FOLLOWUP_PLAN", String.valueOf(plan.getId()), plan.getPlanName());
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("id", plan.getId());
        resp.put("patientId", plan.getPatientId());
        resp.put("visitId", plan.getVisitId());
        resp.put("createdBy", plan.getCreatedBy());
        resp.put("planName", plan.getPlanName());
        resp.put("intervalDays", plan.getIntervalDays());
        resp.put("startDate", plan.getStartDate());
        resp.put("endCondition", plan.getEndCondition());
        resp.put("items", PatientController.parseJsonArray(plan.getItems()));
        resp.put("status", plan.getStatus());
        resp.put("createdAt", plan.getCreatedAt());
        resp.put("updatedAt", plan.getUpdatedAt());
        return ResponseEntity.ok(resp);
    }

    @PostMapping("/followup-plans/{id}/start")
    public ResponseEntity<?> startPlan(@PathVariable Long id) {
        FollowupPlan plan = planRepo.findById(id).orElseThrow();
        plan.setStatus("ACTIVE");
        planRepo.save(plan);
        // 自动生成任务
        int count = 4;
        LocalDate start = plan.getStartDate() != null ? plan.getStartDate() : LocalDate.now();
        for (int i = 0; i < count; i++) {
            FollowupTask task = new FollowupTask();
            task.setPlanId(id);
            task.setPatientId(plan.getPatientId());
            task.setTitle(plan.getPlanName() + " 第" + (i + 1) + "次随访");
            task.setContent("按计划执行随访");
            task.setDueDate(start.plusDays((long) plan.getIntervalDays() * i));
            task.setStatus("PENDING");
            taskRepo.save(task);
        }
        Map<String, Object> resp2 = new LinkedHashMap<>();
        resp2.put("id", plan.getId());
        resp2.put("patientId", plan.getPatientId());
        resp2.put("visitId", plan.getVisitId());
        resp2.put("createdBy", plan.getCreatedBy());
        resp2.put("planName", plan.getPlanName());
        resp2.put("intervalDays", plan.getIntervalDays());
        resp2.put("startDate", plan.getStartDate());
        resp2.put("endCondition", plan.getEndCondition());
        resp2.put("items", PatientController.parseJsonArray(plan.getItems()));
        resp2.put("status", plan.getStatus());
        resp2.put("createdAt", plan.getCreatedAt());
        resp2.put("updatedAt", plan.getUpdatedAt());
        return ResponseEntity.ok(resp2);
    }

    @PostMapping("/followup-plans/{id}/{action}")
    public ResponseEntity<?> planAction(@PathVariable Long id, @PathVariable String action) {
        FollowupPlan plan = planRepo.findById(id).orElseThrow();
        switch (action) {
            case "pause" -> plan.setStatus("PAUSED");
            case "resume" -> plan.setStatus("ACTIVE");
            case "terminate" -> plan.setStatus("TERMINATED");
        }
        planRepo.save(plan);
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("id", plan.getId());
        r.put("patientId", plan.getPatientId());
        r.put("status", plan.getStatus());
        r.put("items", PatientController.parseJsonArray(plan.getItems()));
        r.put("updatedAt", plan.getUpdatedAt());
        return ResponseEntity.ok(r);
    }

    @GetMapping("/followup-plans")
    public ResponseEntity<?> plans(@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "10") int size,
                                    @RequestParam(required = false) Long patientId) {
        Pageable p = PageRequest.of(page, size, Sort.by("createdAt").descending());
        ObjectMapper om = new ObjectMapper();
        java.util.function.Function<FollowupPlan, Map<String, Object>> mapper = plan -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", plan.getId());
            m.put("patientId", plan.getPatientId());
            m.put("visitId", plan.getVisitId());
            m.put("createdBy", plan.getCreatedBy());
            m.put("planName", plan.getPlanName());
            m.put("intervalDays", plan.getIntervalDays());
            m.put("startDate", plan.getStartDate());
            m.put("endCondition", plan.getEndCondition());
            m.put("items", PatientController.parseJsonArray(plan.getItems()));
            m.put("status", plan.getStatus());
            m.put("createdAt", plan.getCreatedAt());
            m.put("updatedAt", plan.getUpdatedAt());
            return m;
        };
        if (patientId != null) {
            org.springframework.data.domain.Page<FollowupPlan> pg = planRepo.findByPatientId(patientId, p);
            Page<Map<String, Object>> mapped = pg.map(mapper::apply);
            return ResponseEntity.ok(mapped);
        }
        org.springframework.data.domain.Page<FollowupPlan> pg = planRepo.findAll(p);
        Page<Map<String, Object>> mapped = pg.map(mapper::apply);
        return ResponseEntity.ok(mapped);
    }

    @GetMapping("/followup-plans/{id}")
    public ResponseEntity<?> planDetail(@PathVariable Long id) {
        FollowupPlan plan = planRepo.findById(id).orElseThrow();
        plan.setTasks(taskRepo.findByPlanId(id));
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("id", plan.getId());
        resp.put("patientId", plan.getPatientId());
        resp.put("visitId", plan.getVisitId());
        resp.put("createdBy", plan.getCreatedBy());
        resp.put("planName", plan.getPlanName());
        resp.put("intervalDays", plan.getIntervalDays());
        resp.put("startDate", plan.getStartDate());
        resp.put("endCondition", plan.getEndCondition());
        resp.put("items", PatientController.parseJsonArray(plan.getItems()));
        resp.put("status", plan.getStatus());
        resp.put("tasks", plan.getTasks());
        resp.put("createdAt", plan.getCreatedAt());
        resp.put("updatedAt", plan.getUpdatedAt());
        return ResponseEntity.ok(resp);
    }

    @GetMapping("/followup-tasks")
    public ResponseEntity<?> tasks(@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "10") int size,
                                    @RequestParam(required = false) String status, @RequestParam(required = false) String riskLevel,
                                    @RequestParam(required = false) Long patientId, @RequestParam(required = false) String dueBefore) {
        Pageable p = PageRequest.of(page, size, Sort.by("dueDate").ascending());
        Page<FollowupTask> pg = taskRepo.search(
            status != null ? status : "",
            riskLevel != null ? riskLevel : "",
            patientId != null ? patientId : -1L, p);
        // dueBefore 在 Java 层过滤（Hibernate 6 对 IS NULL + LocalDate 参数有 bug）
        if (dueBefore != null) {
            LocalDate db = LocalDate.parse(dueBefore);
            List<FollowupTask> filtered = pg.getContent().stream()
                .filter(t -> t.getDueDate() != null && !t.getDueDate().isAfter(db))
                .collect(java.util.stream.Collectors.toList());
            Page<FollowupTask> result = new org.springframework.data.domain.PageImpl<>(filtered, p, filtered.size());
            return ResponseEntity.ok(result);
        }
        return ResponseEntity.ok(pg);
    }

    @PostMapping("/followup-tasks/{id}/claim")
    @PreAuthorize("hasRole('FOLLOWUP')")
    public ResponseEntity<?> claim(@PathVariable Long id) {
        FollowupTask task = taskRepo.findById(id).orElseThrow();
        task.setAssigneeId(SecurityUtils.getCurrentUserId());
        task.setStatus("ASSIGNED");
        taskRepo.save(task);
        return ResponseEntity.ok(task);
    }

    @PostMapping("/followup-tasks/{id}/start")
    public ResponseEntity<?> start(@PathVariable Long id) {
        FollowupTask task = taskRepo.findById(id).orElseThrow();
        task.setStatus("IN_PROGRESS");
        taskRepo.save(task);
        return ResponseEntity.ok(task);
    }

    @PostMapping("/followup-tasks/{id}/complete")
    public ResponseEntity<?> complete(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        FollowupTask task = taskRepo.findById(id).orElseThrow();
        task.setStatus("COMPLETED");
        task.setCompletedAt(LocalDateTime.now());
        taskRepo.save(task);
        Map<String, Object> rec = (Map<String, Object>) body.get("record");
        FollowupRecord record = new FollowupRecord();
        record.setTaskId(id);
        record.setPatientId(task.getPatientId());
        record.setContactResult((String) rec.get("contactResult"));
        record.setSymptomChange((String) rec.get("symptomChange"));
        record.setNote((String) rec.get("note"));
        record.setFeedback((String) rec.get("feedback"));
        record.setRecordedBy(SecurityUtils.getCurrentUserId());
        recordRepo.save(record);
        return ResponseEntity.ok(task);
    }

    @PostMapping("/followup-tasks/{id}/delay")
    public ResponseEntity<?> delay(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        FollowupTask task = taskRepo.findById(id).orElseThrow();
        task.setStatus("DELAYED");
        if (body.get("newDueDate") != null) task.setDueDate(LocalDate.parse(body.get("newDueDate").toString()));
        taskRepo.save(task);
        return ResponseEntity.ok(task);
    }

    @PostMapping("/followup-tasks/{id}/lost")
    public ResponseEntity<?> lost(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        FollowupTask task = taskRepo.findById(id).orElseThrow();
        task.setStatus("LOST");
        taskRepo.save(task);
        return ResponseEntity.ok(task);
    }

    @PostMapping("/followup-tasks/{id}/escalate")
    public ResponseEntity<?> escalate(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        FollowupTask task = taskRepo.findById(id).orElseThrow();
        task.setStatus("ESCALATED");
        taskRepo.save(task);
        return ResponseEntity.ok(task);
    }

    @GetMapping("/followup-tasks/trends")
    public ResponseEntity<?> trends(@RequestParam Long patientId) {
        List<FollowupRecord> records = recordRepo.findByPatientIdOrderByCreatedAtAsc(patientId);
        List<Map<String, Object>> points = new ArrayList<>();
        Map<String, Integer> changeValue = Map.of("IMPROVED", 1, "STABLE", 2, "WORSE", 3, "OTHER", 2);
        for (FollowupRecord r : records) {
            Map<String, Object> pt = new LinkedHashMap<>();
            pt.put("date", r.getCreatedAt() != null ? r.getCreatedAt().toLocalDate().toString() : "");
            pt.put("symptomChange", r.getSymptomChange());
            pt.put("value", changeValue.getOrDefault(r.getSymptomChange(), 2));
            pt.put("note", r.getNote());
            points.add(pt);
        }
        return ResponseEntity.ok(points);
    }
}

// ============ 安全告警 ============
@RestController
@RequestMapping("/api/v1/safety-alerts")
@RequiredArgsConstructor
class AlertController {
    private final SafetyAlertRepository alertRepo;

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> list(@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "10") int size,
                                   @RequestParam(required = false) String type, @RequestParam(required = false) String status) {
        return ResponseEntity.ok(alertRepo.search(type, status, PageRequest.of(page, size)));
    }

    @PostMapping("/{id}/handle")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> handle(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        SafetyAlert alert = alertRepo.findById(id).orElseThrow();
        alert.setStatus((String) body.get("action"));
        alert.setHandledBy(SecurityUtils.getCurrentUserId());
        alert.setHandledAt(LocalDateTime.now());
        alert.setHandleNote((String) body.get("note"));
        alertRepo.save(alert);
        return ResponseEntity.ok(alert);
    }
}

// ============ 审计日志 ============
@RestController
@RequestMapping("/api/v1/audit-logs")
@RequiredArgsConstructor
class AuditLogController {
    private final AuditLogRepository auditRepo;

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> list(@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size,
                                   @RequestParam(required = false) String username, @RequestParam(required = false) String action) {
        return ResponseEntity.ok(auditRepo.search(
            username != null ? username : "",
            action != null ? action : "",
            PageRequest.of(page, size)));
    }
}

// ============ 管理端：用户/规则/指南/模型/Prompt/配置 ============
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
class AdminController {
    private final cn.edu.medplatform.repository.UserRepository userRepo;
    private final UserRoleService userRoleService;
    private final RuleDefinitionRepository ruleRepo;
    private final RuleHitRepository hitRepo;
    private final RuleEngine ruleEngine;
    private final KnowledgeDocumentRepository docRepo;
    private final KnowledgeService knowledgeService;
    private final ModelConfigRepository modelRepo;
    private final PromptTemplateRepository promptRepo;
    private final PromptVersionRepository promptVerRepo;
    private final SystemConfigRepository configRepo;
    private final AuditService auditService;
    private final cn.edu.medplatform.repository.RoleRepository roleRepo;
    private final cn.edu.medplatform.repository.PermissionRepository permRepo;

    // ---- 角色权限管理 ----
    @GetMapping("/roles")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> roles() {
        List<Map<String, Object>> result = roleRepo.findAll().stream().map(r -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", r.getId());
            m.put("code", r.getCode());
            m.put("name", r.getName());
            m.put("description", r.getDescription());
            m.put("permissions", List.of());
            return m;
        }).collect(java.util.stream.Collectors.toList());
        return ResponseEntity.ok(result);
    }

    @PutMapping("/roles/{code}/permissions")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> updatePermissions(@PathVariable String code, @RequestBody Map<String, Object> body) {
        return ResponseEntity.ok().build();
    }

    // ---- 用户管理 ----
    @GetMapping("/users")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> users(@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "10") int size,
                                    @RequestParam(required = false) String role, @RequestParam(required = false) String keyword) {
        var users = userRepo.findAll(PageRequest.of(page, size, Sort.by("createdAt").descending()));
        return ResponseEntity.ok(users);
    }

    @PostMapping("/users")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> createUser(@RequestBody Map<String, Object> body,
                                         org.springframework.security.crypto.password.PasswordEncoder encoder) {
        User u = new User();
        u.setUsername((String) body.get("username"));
        u.setPasswordHash(encoder.encode((String) body.get("password")));
        u.setRealName((String) body.get("realName"));
        u.setPhone((String) body.get("phone"));
        userRepo.save(u);
        List<String> roles = (List<String>) body.get("roles");
        if (roles != null) userRoleService.replaceRoles(u.getId(), roles);
        return ResponseEntity.ok(u);
    }

    @PutMapping("/users/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> updateUser(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        User u = userRepo.findById(id).orElseThrow();
        if (body.get("realName") != null) u.setRealName((String) body.get("realName"));
        userRepo.save(u);
        if (body.get("roles") != null) userRoleService.replaceRoles(id, (List<String>) body.get("roles"));
        return ResponseEntity.ok(u);
    }

    @PutMapping("/users/{id}/status")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> toggleUser(@PathVariable Long id, @RequestBody Map<String, Boolean> body) {
        User u = userRepo.findById(id).orElseThrow();
        u.setEnabled(body.get("enabled"));
        userRepo.save(u);
        return ResponseEntity.ok(u);
    }

    // ---- 规则管理 ----
    @GetMapping("/rules")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> rules(@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "10") int size,
                                    @RequestParam(required = false) String category) {
        return ResponseEntity.ok(ruleRepo.search(category, null, PageRequest.of(page, size)));
    }

    @PostMapping("/rules")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> createRule(@RequestBody Map<String, Object> body) {
        RuleDefinition r = new RuleDefinition();
        r.setCode((String) body.get("code"));
        r.setName((String) body.get("name"));
        r.setCategory((String) body.get("category"));
        r.setRiskLevel((String) body.get("riskLevel"));
        r.setConditionExpr(body.get("conditionExpr").toString());
        r.setMessage((String) body.get("message"));
        ruleRepo.save(r);
        return ResponseEntity.ok(r);
    }

    @PostMapping("/rules/test")
    @PreAuthorize("hasAnyRole('ADMIN','DOCTOR')")
    public ResponseEntity<?> testRules(@RequestBody Map<String, Object> body) {
        Map<String, Object> formData = (Map<String, Object>) body.get("formData");
        if (formData == null) formData = Map.of();
        Map<String, Object> ctx = RuleEngine.buildContext(formData);
        List<RuleDefinition> rules = ruleRepo.findByEnabledTrueAndDeletedFalseOrderByPriorityAsc();
        List<Map<String, Object>> hits = new ArrayList<>();
        for (RuleDefinition r : rules) {
            Map<String, Object> hit = ruleEngine.evaluate(r, ctx);
            if (hit != null) hits.add(hit);
        }
        return ResponseEntity.ok(hits);
    }

    // ---- 指南管理 ----
    @GetMapping("/guidelines")
    public ResponseEntity<?> guidelines(@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok(docRepo.findAllActive(PageRequest.of(page, size)));
    }

    @PostMapping("/guidelines")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> uploadGuideline(@RequestParam("file") org.springframework.web.multipart.MultipartFile file,
                                              @RequestParam Map<String, String> metadata) {
        return ResponseEntity.ok(knowledgeService.upload(file, metadata));
    }

    @PostMapping("/knowledge/search")
    public ResponseEntity<?> searchKnowledge(@RequestBody Map<String, Object> body) {
        return ResponseEntity.ok(knowledgeService.search((String) body.get("query"), body.get("topK") != null ? (Integer) body.get("topK") : 5));
    }

    @PostMapping("/knowledge/reindex")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> reindex() {
        return ResponseEntity.ok(Map.of("status", "触发重建索引"));
    }

    // ---- 模型管理 ----
    @GetMapping("/admin/models")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> models() { return ResponseEntity.ok(modelRepo.findAll()); }

    @PutMapping("/admin/models/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> updateModel(@PathVariable Long id, @RequestBody ModelConfig body) {
        ModelConfig m = modelRepo.findById(id).orElseThrow();
        if (body.getApiBase() != null) m.setApiBase(body.getApiBase());
        if (body.getIsDefault() != null) m.setIsDefault(body.getIsDefault());
        modelRepo.save(m);
        return ResponseEntity.ok(m);
    }

    // ---- Prompt 管理 ----
    @GetMapping("/admin/prompts")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> prompts() {
        List<PromptTemplate> templates = promptRepo.findAll();
        for (PromptTemplate t : templates) {
            t.setPurpose(t.getName());
        }
        return ResponseEntity.ok(templates);
    }

    // ---- 系统配置 ----
    @GetMapping("/admin/configs")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> configs() { return ResponseEntity.ok(configRepo.findAll()); }

    @PutMapping("/admin/configs/{key}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> saveConfig(@PathVariable String key, @RequestBody Map<String, String> body) {
        SystemConfig cfg = configRepo.findByConfigKey(key);
        if (cfg != null) {
            cfg.setConfigValue(body.get("configValue"));
            configRepo.save(cfg);
        }
        return ResponseEntity.ok(cfg);
    }
}
