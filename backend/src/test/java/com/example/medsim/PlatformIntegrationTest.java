package com.example.medsim;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PlatformIntegrationTest {
    private static final String TEMPLATE = "HYPERTENSION_TEACHING_V1";

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired JdbcTemplate jdbc;
    @MockBean AiClient aiClient;

    @BeforeEach
    void cleanBusinessData() {
        jdbc.execute("TRUNCATE TABLE audit_logs, safety_alerts, followup_tasks, followup_plans, citations, agent_runs, triage_results, symptoms, visits, guideline_chunks, guideline_versions, guidelines CASCADE");
        when(aiClient.analyze(anyString(), any(), anyList(), any())).thenThrow(new IllegalStateException("AI_UNAVAILABLE_TEST"));
    }

    @Test
    void completeRuleFirstFlowAndEnforceRbac() throws Exception {
        String patient = login("patient");
        String clinician = login("clinician");
        String admin = login("admin");
        JsonNode draft = createVisit(patient, emergencyVisit());
        JsonNode submitted = submit(patient, draft.get("id").asText(), "integration-flow-001");

        assertThat(submitted.at("/triage/ruleUrgency").asText()).isEqualTo("EMERGENCY");
        assertThat(submitted.at("/runs/0/status").asText()).isEqualTo("FAILED");
        mvc.perform(get("/api/v1/clinician/visits").header("Authorization", bearer(patient)))
            .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("AUTH_FORBIDDEN"));
        mvc.perform(get("/api/v1/clinician/visits").header("Authorization", bearer(clinician)))
            .andExpect(status().isOk()).andExpect(jsonPath("$[0].id").value(draft.get("id").asText()));
        mvc.perform(get("/api/v1/admin/safety-alerts").header("Authorization", bearer(admin)))
            .andExpect(status().isOk()).andExpect(jsonPath("$[0].category").value("AI_UNAVAILABLE_OR_INVALID"));

        mvc.perform(post("/api/v1/triage-results/{id}/review", submitted.at("/triage/id").asText())
                .header("Authorization", bearer(clinician)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"decision\":\"ACCEPT\",\"reason\":\"已核对规则与合成信息\",\"finalUrgency\":\"ROUTINE\"}"))
            .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("TRIAGE_RULE_DOWNGRADE_FORBIDDEN"));
    }

    @Test
    void requireIdempotencyKeyAndSanitizeBeforePersistence() throws Exception {
        String patient = login("patient");
        JsonNode draft = createVisit(patient, """
            {"chiefComplaint":"手机号 13800138000，姓名：张三","freeText":"身份证 11010519491231002X，邮箱 test@example.com","symptoms":[
              {"code":"HEADACHE","name":"头痛 13900139000","severity":3,"onset":"地址：北京市海淀区测试路 1 号"}]}
            """);

        assertThat(draft.get("chiefComplaint").asText()).doesNotContain("13800138000", "张三").contains("[已脱敏]");
        assertThat(draft.get("freeText").asText()).doesNotContain("11010519491231002X", "test@example.com");
        assertThat(draft.at("/symptoms/0/name").asText()).doesNotContain("13900139000");
        String stored = jdbc.queryForObject("SELECT chief_complaint || ' ' || free_text FROM visits WHERE id=?", String.class, UUID.fromString(draft.get("id").asText()));
        assertThat(stored).doesNotContain("13800138000", "11010519491231002X", "test@example.com", "张三");

        mvc.perform(post("/api/v1/visits/{id}/submit", draft.get("id").asText()).header("Authorization", bearer(patient)))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_REQUIRED"));
    }

    @Test
    void rejectIsTerminalAndCannotCreateFollowupPlan() throws Exception {
        String patient = login("patient");
        String clinician = login("clinician");
        JsonNode submitted = submit(patient, createVisit(patient, routineVisit()).get("id").asText(), "reject-flow-001");

        String reviewed = mvc.perform(post("/api/v1/triage-results/{id}/review", submitted.at("/triage/id").asText())
                .header("Authorization", bearer(clinician)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"decision\":\"REJECT\",\"reason\":\"合成病例信息不足，不生成随访计划\"}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("REJECTED"))
            .andExpect(jsonPath("$.triage.reviewDecision").value("REJECT"))
            .andReturn().getResponse().getContentAsString();

        mvc.perform(post("/api/v1/followup-plans").header("Authorization", bearer(clinician))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"visitId\":\"" + mapper.readTree(reviewed).get("id").asText() + "\",\"templateCode\":\"" + TEMPLATE + "\"}"))
            .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("FOLLOWUP_REQUIRES_REVIEW"));
    }

    @Test
    void rejectUnknownAndDuplicatePlansThenEnforceTaskStateMachine() throws Exception {
        String patient = login("patient");
        String clinician = login("clinician");
        String staff = login("followup");
        JsonNode submitted = submit(patient, createVisit(patient, routineVisit()).get("id").asText(), "task-flow-001");
        String visitId = submitted.get("id").asText();
        review(clinician, submitted.at("/triage/id").asText(), "ACCEPT", "ROUTINE");

        mvc.perform(post("/api/v1/followup-plans").header("Authorization", bearer(clinician))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"visitId\":\"" + visitId + "\",\"templateCode\":\"UNKNOWN_TEMPLATE\"}"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("FOLLOWUP_TEMPLATE_UNKNOWN"));

        JsonNode plan = createPlan(clinician, visitId);
        mvc.perform(post("/api/v1/followup-plans").header("Authorization", bearer(clinician))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"visitId\":\"" + visitId + "\",\"templateCode\":\"" + TEMPLATE + "\"}"))
            .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("FOLLOWUP_PLAN_ALREADY_EXISTS"));
        mvc.perform(post("/api/v1/followup-plans/{id}/activate", plan.get("id").asText()).header("Authorization", bearer(clinician)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.tasks.length()").value(4));

        JsonNode tasks = mapper.readTree(mvc.perform(get("/api/v1/followup-tasks/mine").header("Authorization", bearer(staff)))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        String taskId = tasks.get(0).get("id").asText();
        updateTask(staff, taskId, "COMPLETED", "跳过开始").andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("FOLLOWUP_TASK_INVALID_TRANSITION"));
        updateTask(staff, taskId, "IN_PROGRESS", "").andExpect(status().isOk()).andExpect(jsonPath("$.status").value("IN_PROGRESS"));
        updateTask(staff, taskId, "COMPLETED", "").andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("FOLLOWUP_RESULT_REQUIRED"));
        updateTask(staff, taskId, "COMPLETED", "已联系 13800138000 并完成教学记录").andExpect(status().isOk())
            .andExpect(jsonPath("$.resultSummary").value("已联系 [已脱敏] 并完成教学记录"));
        updateTask(staff, taskId, "IN_PROGRESS", "试图回退").andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("FOLLOWUP_TASK_INVALID_TRANSITION"));
    }

    @Test
    void pgvectorSchemaAndTokenProtectedKnowledgeSearchWork() throws Exception {
        assertThat(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM pg_extension WHERE extname='vector')", Boolean.class)).isTrue();
        assertThat(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM pg_indexes WHERE indexname='idx_guideline_chunks_embedding' AND indexdef ILIKE '%hnsw%')", Boolean.class)).isTrue();
        seedKnowledgeFixture();

        String searchBody = "{\"symptomCodes\":[\"CHEST_PAIN\",\"DYSPNEA\"],\"query\":\"胸痛 呼吸困难\",\"limit\":3}";
        mvc.perform(post("/internal/v1/knowledge/search").header("X-Internal-Token", "wrong-token")
                .contentType(MediaType.APPLICATION_JSON).content(searchBody))
            .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("INTERNAL_UNAUTHORIZED"));
        mvc.perform(post("/internal/v1/knowledge/search").header("X-Internal-Token", "change-me-local-only")
                .contentType(MediaType.APPLICATION_JSON).content(searchBody))
            .andExpect(status().isOk()).andExpect(jsonPath("$[0].guidelineId").value("test-red-flags"))
            .andExpect(jsonPath("$[0].chunkId").value("chunk-test-red-flag-001"))
            .andExpect(jsonPath("$[0].sourceUrl").value("https://example.org/test-source"));
    }

    private JsonNode createVisit(String token, String body) throws Exception {
        String json = mvc.perform(post("/api/v1/visits").header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("DRAFT"))
            .andReturn().getResponse().getContentAsString();
        return mapper.readTree(json);
    }

    private JsonNode submit(String token, String visitId, String key) throws Exception {
        String json = mvc.perform(post("/api/v1/visits/{id}/submit", visitId)
                .header("Authorization", bearer(token)).header("Idempotency-Key", key))
            .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("PENDING_REVIEW"))
            .andReturn().getResponse().getContentAsString();
        return mapper.readTree(json);
    }

    private void review(String token, String triageId, String decision, String urgency) throws Exception {
        mvc.perform(post("/api/v1/triage-results/{id}/review", triageId).header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"decision\":\"" + decision + "\",\"reason\":\"已核对合成信息\",\"finalUrgency\":\"" + urgency + "\"}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("REVIEWED"));
    }

    private JsonNode createPlan(String token, String visitId) throws Exception {
        String json = mvc.perform(post("/api/v1/followup-plans").header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"visitId\":\"" + visitId + "\",\"templateCode\":\"" + TEMPLATE + "\"}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("DRAFT"))
            .andReturn().getResponse().getContentAsString();
        return mapper.readTree(json);
    }

    private org.springframework.test.web.servlet.ResultActions updateTask(String token, String taskId, String status, String summary) throws Exception {
        return mvc.perform(patch("/api/v1/followup-tasks/{id}", taskId).header("Authorization", bearer(token))
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"status\":\"" + status + "\",\"resultSummary\":\"" + summary + "\"}"));
    }

    private void seedKnowledgeFixture() {
        UUID guidelineId = UUID.randomUUID();
        UUID versionId = UUID.randomUUID();
        UUID chunkId = UUID.randomUUID();
        jdbc.update("INSERT INTO guidelines(id,guideline_id,title,publisher,source_url,license_note) VALUES (?,?,?,?,?,?)",
            guidelineId, "test-red-flags", "测试红旗教学资料", "测试发布方", "https://example.org/test-source", "仅用于自动化测试");
        jdbc.update("INSERT INTO guideline_versions(id,guideline_id,version_id,version_label,language,object_key,sha256,active) VALUES (?,?,?,?,?,?,?,TRUE)",
            versionId, guidelineId, "test-red-flags-v1", "v1", "zh-CN", "test/v1.md", "0".repeat(64));
        jdbc.update("INSERT INTO guideline_chunks(id,version_id,chunk_id,section_name,topics,content,embedding) VALUES (?,?,?,?,?,?,CAST(? AS vector))",
            chunkId, versionId, "chunk-test-red-flag-001", "胸痛红旗", "CHEST_PAIN DYSPNEA", "胸痛伴呼吸困难需立即人工评估", unitVector());
    }

    private String unitVector() {
        return IntStream.range(0, 64).mapToObj(i -> i == 0 ? "1" : "0").collect(Collectors.joining(",", "[", "]"));
    }

    private String login(String username) throws Exception {
        String body = mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"" + username + "\",\"password\":\"Demo123!\"}"))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return mapper.readTree(body).get("accessToken").asText();
    }

    private String bearer(String token) { return "Bearer " + token; }

    private String routineVisit() {
        return "{\"chiefComplaint\":\"合成轻度头痛\",\"freeText\":\"仅为教学模拟\",\"symptoms\":[{\"code\":\"HEADACHE\",\"name\":\"头痛\",\"severity\":3,\"onset\":\"2 天前\"}]}";
    }

    private String emergencyVisit() {
        return "{\"chiefComplaint\":\"合成胸痛伴呼吸困难\",\"freeText\":\"仅为教学模拟\",\"symptoms\":[{\"code\":\"CHEST_PAIN\",\"name\":\"胸痛\",\"severity\":8,\"onset\":\"1 小时前\"},{\"code\":\"DYSPNEA\",\"name\":\"呼吸困难\",\"severity\":6,\"onset\":\"30 分钟前\"}]}";
    }
}
