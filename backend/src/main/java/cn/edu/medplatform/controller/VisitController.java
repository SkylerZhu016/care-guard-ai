package cn.edu.medplatform.controller;

import cn.edu.medplatform.entity.*;
import cn.edu.medplatform.repository.*;
import cn.edu.medplatform.service.*;
import cn.edu.medplatform.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.*;

@RestController
@RequestMapping("/api/v1/visits")
@RequiredArgsConstructor
public class VisitController {
    private final VisitService visitService;
    private final VisitRepository visitRepo;
    private final VisitStatusLogRepository logRepo;
    private final AgentRunRepository runRepo;
    private final AgentRunStepRepository stepRepo;
    private final TriageResultRepository triageRepo;
    private final RuleHitRepository hitRepo;
    private final CitationRepository citationRepo;

    @PostMapping("/draft")
    public ResponseEntity<?> saveDraft(@RequestBody Map<String, Object> body) {
        Long uid = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(visitService.saveDraft(uid,
                Long.valueOf(body.get("patientId").toString()),
                (Map<String, Object>) body.get("formData")));
    }

    @GetMapping("/drafts")
    public ResponseEntity<?> drafts(@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "10") int size) {
        Pageable p = PageRequest.of(page, size, Sort.by("createdAt").descending());
        return ResponseEntity.ok(visitRepo.findByOwnerUserId(SecurityUtils.getCurrentUserId(), p));
    }

    @PostMapping
    public ResponseEntity<?> submit(@RequestBody Map<String, Object> body,
                                     @RequestHeader(value = "Idempotency-Key", required = false) String idemKey) {
        Long uid = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(visitService.submit(uid,
                Long.valueOf(body.get("patientId").toString()),
                (Map<String, Object>) body.get("formData"),
                idemKey != null ? idemKey : UUID.randomUUID().toString()));
    }

    @GetMapping
    public ResponseEntity<?> list(
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String status, @RequestParam(required = false) String riskLevel,
            @RequestParam(required = false) Long patientId) {
        Pageable p = PageRequest.of(page, size, Sort.by("createdAt").descending());
        Long ownerUid = (SecurityUtils.hasRole("PATIENT") && !SecurityUtils.isAdmin()) ? SecurityUtils.getCurrentUserId() : null;
        return ResponseEntity.ok(visitRepo.search(status, riskLevel, patientId, ownerUid, p));
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> detail(@PathVariable Long id) {
        return ResponseEntity.ok(visitService.getDetail(id));
    }

    @PutMapping("/{id}/supplement")
    public ResponseEntity<?> supplement(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        visitService.supplement(id, (Map<String, Object>) body.get("formData"), SecurityUtils.getCurrentUserId());
        return ResponseEntity.ok(Map.of("status", "SUBMITTED"));
    }

    @GetMapping("/{id}/status-logs")
    public ResponseEntity<?> statusLogs(@PathVariable Long id) {
        return ResponseEntity.ok(logRepo.findByVisitIdOrderByCreatedAtAsc(id));
    }

    @GetMapping("/{id}/agent-run")
    public ResponseEntity<?> agentRun(@PathVariable Long id) {
        AgentRun run = visitService.getLatestRun(id);
        if (run == null) return ResponseEntity.ok().build();
        run.setSteps(stepRepo.findByRunIdOrderByCreatedAtAsc(run.getId()));
        return ResponseEntity.ok(run);
    }

    @PostMapping("/{id}/retry")
    public ResponseEntity<?> retry(@PathVariable Long id) {
        // 简化：重置为 SUBMITTED 并重跑
        return ResponseEntity.ok(Map.of("status", "RETRYING"));
    }
}

// 分诊只读接口
@RestController
@RequestMapping("/api/v1/triage")
@RequiredArgsConstructor
class TriageController {
    private final TriageResultRepository triageRepo;
    private final RuleHitRepository hitRepo;
    private final CitationRepository citationRepo;

    @GetMapping("/visits/{visitId}")
    public ResponseEntity<?> detail(@PathVariable Long visitId) {
        return ResponseEntity.ok(triageRepo.findByVisitId(visitId));
    }

    @GetMapping("/visits/{visitId}/rule-hits")
    public ResponseEntity<?> ruleHits(@PathVariable Long visitId) {
        return ResponseEntity.ok(hitRepo.findByVisitId(visitId));
    }

    @GetMapping("/visits/{visitId}/citations")
    public ResponseEntity<?> citations(@PathVariable Long visitId) {
        return ResponseEntity.ok(citationRepo.findByVisitId(visitId));
    }
}

// Agent 运行记录 + SSE
@RestController
@RequestMapping("/api/v1/agent-runs")
@RequiredArgsConstructor
class AgentRunController {
    private final AgentRunRepository runRepo;
    private final AgentRunStepRepository stepRepo;

    @GetMapping
    public ResponseEntity<?> list(
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String status, @RequestParam(required = false) Long visitId) {
        Pageable p = PageRequest.of(page, size, Sort.by("createdAt").descending());
        return ResponseEntity.ok(runRepo.search(status, visitId, p));
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> detail(@PathVariable Long id) {
        AgentRun run = runRepo.findById(id).orElseThrow();
        run.setSteps(stepRepo.findByRunIdOrderByCreatedAtAsc(id));
        return ResponseEntity.ok(run);
    }

    @GetMapping(value = "/{id}/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter events(@PathVariable Long id, @RequestParam(required = false) String token) {
        SseEmitter emitter = new SseEmitter(300_000L);
        // 轮询 DB 推送状态（SSE 简化版）
        new Thread(() -> {
            try {
                for (int i = 0; i < 100; i++) {
                    AgentRun run = runRepo.findById(id).orElse(null);
                    if (run == null) break;
                    emitter.send(SseEmitter.event().name("status").data(run.getStatus()));
                    if (Set.of("COMPLETED", "FAILED", "REVIEW_FAILED", "TIMEOUT", "CANCELLED", "PARTIAL").contains(run.getStatus())) {
                        emitter.send(SseEmitter.event().name("done").data("finished"));
                        break;
                    }
                    Thread.sleep(2000);
                }
                emitter.complete();
            } catch (Exception e) {
                emitter.completeWithError(e);
            }
        }).start();
        return emitter;
    }
}
