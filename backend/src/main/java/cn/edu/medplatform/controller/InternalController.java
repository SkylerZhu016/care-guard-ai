package cn.edu.medplatform.controller;

import cn.edu.medplatform.service.VisitService;
import cn.edu.medplatform.service.AiServiceClient;
import cn.edu.medplatform.service.AuditService;
import cn.edu.medplatform.service.KnowledgeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/** AI 服务回调后端的内部接口（X-Internal-Token 鉴权） */
@Slf4j
@RestController
@RequestMapping("/api/internal")
@RequiredArgsConstructor
public class InternalController {
    private final VisitService visitService;
    private final AuditService auditService;
    private final KnowledgeService knowledgeService;

    @Value("${app.internal-token}") private String internalToken;

    @PostMapping("/agent-runs/{runId}/completed")
    public ResponseEntity<?> onPipelineCompleted(
            @PathVariable Long runId,
            @RequestBody Map<String, String> body,
            @RequestHeader("X-Internal-Token") String token) {
        if (!internalToken.equals(token)) return ResponseEntity.status(403).build();
        String runStatus = body.get("runStatus");
        log.info("AI 回调: runId={}, status={}", runId, runStatus);
        visitService.onAiCompleted(runId, runStatus);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/knowledge/documents/{docId}/ingested")
    public ResponseEntity<?> onIngested(
            @PathVariable Long docId,
            @RequestBody Map<String, Object> body,
            @RequestHeader("X-Internal-Token") String token) {
        if (!internalToken.equals(token)) return ResponseEntity.status(403).build();
        knowledgeService.onIngested(docId, (Integer) body.get("chunkCount"), (String) body.get("status"));
        return ResponseEntity.ok().build();
    }
}
