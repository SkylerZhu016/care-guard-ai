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
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired JdbcTemplate jdbc;
    @MockBean AiClient aiClient;

    @BeforeEach
    void cleanBusinessData() {
        jdbc.execute("TRUNCATE TABLE visit_complaint_analyses, visit_supplements, patient_profiles, audit_logs, safety_alerts, followup_tasks, followup_plans, citations, agent_runs, triage_results, symptoms, visits, guideline_chunks, guideline_versions, guidelines CASCADE");
        when(aiClient.analyze(anyString(), any(), anyList(), any(), any())).thenThrow(new IllegalStateException("AI_UNAVAILABLE_TEST"));
    }

    @Test
    void v1WritesAreGoneAndPatientRoleIsMigrated() throws Exception {
        String patient = login("patient");
        mvc.perform(post("/api/v1/visits").header("Authorization", bearer(patient)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"chiefComplaint\":\"头痛\",\"symptoms\":[{\"code\":\"HEADACHE\",\"name\":\"头痛\",\"severity\":3}]}"))
            .andExpect(status().isGone()).andExpect(jsonPath("$.code").value("INTAKE_V1_DEPRECATED"));
        mvc.perform(get("/api/v1/me").header("Authorization", bearer(patient)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.role").value("PATIENT"));
    }

    @Test
    void unsupportedSymptomsRequireManualReviewAndNeverWriteSeverity() throws Exception {
        String patient = login("patient");
        JsonNode draft = createV2(patient, unsupportedVisit());
        JsonNode submitted = submitV2(patient, draft.get("id").asText(), "unsupported-001");
        assertThat(submitted.at("/triage/ruleUrgency").isMissingNode()).isTrue();
        assertThat(submitted.at("/triage/coverageStatus").asText()).isEqualTo("NONE");
        assertThat(submitted.at("/triage/assessmentStatus").asText()).isEqualTo("REQUIRES_MANUAL_REVIEW");
        assertThat(jdbc.queryForObject("SELECT legacy_severity IS NULL FROM symptoms WHERE visit_id=?", Boolean.class,
            UUID.fromString(draft.get("id").asText()))).isTrue();
        assertThat(jdbc.queryForObject("SELECT report_source FROM symptoms WHERE visit_id=?", String.class,
            UUID.fromString(draft.get("id").asText()))).isEqualTo("USER_SELECTED");
    }

    @Test
    void deterministicEmergencyRulesAndMixedCoverageArePreserved() throws Exception {
        String patient = login("patient");
        JsonNode submitted = submitV2(patient, createV2(patient, emergencyMixedVisit()).get("id").asText(), "emergency-001");
        assertThat(submitted.at("/triage/ruleUrgency").asText()).isEqualTo("EMERGENCY");
        assertThat(submitted.at("/triage/coverageStatus").asText()).isEqualTo("PARTIAL");
        assertThat(submitted.at("/triage/ruleReasons").toString()).contains("CHEST_PAIN_WITH_DYSPNEA", "UNSUPPORTED_SYMPTOMS_REQUIRE_REVIEW");
    }

    @Test
    void profileIsSnapshottedAndSubmittedVisitAcceptsSupplement() throws Exception {
        String patient = login("patient");
        mvc.perform(put("/api/v2/patient-profile").header("Authorization", bearer(patient)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"ageBand\":\"ADULT\",\"physiologicalInfoStatus\":\"UNKNOWN\",\"physiologicalInfo\":\"\",\"chronicConditionsStatus\":\"PROVIDED\",\"chronicConditions\":[\"高血压\"],\"allergiesStatus\":\"NONE\",\"allergies\":[],\"longTermMedicationsStatus\":\"UNKNOWN\",\"longTermMedications\":[]}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.chronicConditions[0]").value("高血压"));
        JsonNode submitted = submitV2(patient, createV2(patient, unsupportedVisit()).get("id").asText(), "profile-001");
        assertThat(submitted.at("/profileSnapshot/chronicConditions/0").asText()).isEqualTo("高血压");
        mvc.perform(post("/api/v2/visits/{id}/supplements", submitted.get("id").asText())
                .header("Authorization", bearer(patient)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"content\":\"补充：今天下午开始恶心\"}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.content").value("补充：今天下午开始恶心"));
    }

    @Test
    void catalogAndClinicianQueueExposeV2Contract() throws Exception {
        String patient = login("patient"); String clinician = login("clinician");
        mvc.perform(get("/api/v2/intake-catalog").header("Authorization", bearer(patient)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.version").value("intake-catalog-2026.07.2"))
            .andExpect(jsonPath("$.symptoms[0].questions").isArray());
        JsonNode submitted = submitV2(patient, createV2(patient, unsupportedVisit()).get("id").asText(), "queue-001");
        mvc.perform(get("/api/v2/clinician/visits").header("Authorization", bearer(clinician)))
            .andExpect(status().isOk()).andExpect(jsonPath("$[0].id").value(submitted.get("id").asText()));
        mvc.perform(get("/api/v2/clinician/visits").header("Authorization", bearer(patient)))
            .andExpect(status().isForbidden());
    }

    @Test
    void complaintAiStructureIsStoredConfirmedAndDoesNotReplaceSelectedSymptoms() throws Exception {
        when(aiClient.structureComplaint(anyString(), anyList(), anyList(), any())).thenReturn(new AiComplaintResult(
            "患者自述今天头痛并伴恶心。",
            java.util.List.of(new ComplaintTagView("NAUSEA_VOMITING", "恶心或呕吐", "消化系统", "ai_extracted", 0.91, "恶心", "proposed")),
            new ComplaintFactsView("今天", "", "头部", "", java.util.List.of(), java.util.List.of(), java.util.List.of("恶心"), ""),
            java.util.List.of(), java.util.List.of("目前是否仍存在？"), java.util.List.of("病因不确定"),
            "fake", "fake-v1", 12L, "AI 仅用于整理患者表述，不能替代医生诊断。"));
        String patient = login("patient");
        JsonNode draft = createV2(patient, unsupportedVisit());
        String id = draft.get("id").asText();
        mvc.perform(post("/api/v2/visits/{id}/analyze-complaint", id).header("Authorization", bearer(patient)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("SUCCEEDED"))
            .andExpect(jsonPath("$.tags[0].source").value("ai_extracted"));
        mvc.perform(put("/api/v2/visits/{id}/complaint-structure", id).header("Authorization", bearer(patient))
                .contentType(MediaType.APPLICATION_JSON).content("""
                    {"normalizedSummary":"患者确认：今天头痛并伴恶心。","tags":[{"code":"NAUSEA_VOMITING","displayName":"恶心或呕吐","category":"消化系统","source":"ai_extracted","confidence":0.91,"evidenceText":"恶心","confirmationStatus":"confirmed"}],"structuredFacts":{"duration":"今天","onset":"","location":"头部","character":"","aggravatingFactors":[],"relievingFactors":[],"associatedSymptoms":["恶心"],"activityImpact":""},"riskSignals":[],"missingQuestions":["目前是否仍存在？"],"uncertainties":["病因不确定"]}
                    """))
            .andExpect(status().isOk()).andExpect(jsonPath("$.tags[0].confirmationStatus").value("confirmed"));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM symptoms WHERE visit_id=?", Integer.class, UUID.fromString(id))).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT confirmed_json IS NOT NULL FROM visit_complaint_analyses WHERE visit_id=?", Boolean.class, UUID.fromString(id))).isTrue();
    }

    @Test
    void invalidComplaintModelOutputIsDistinguishedFromServiceFailure() throws Exception {
        when(aiClient.structureComplaint(anyString(), anyList(), anyList(), any()))
            .thenThrow(new IllegalStateException("AI_INVALID_JSON_OBJECT"));
        String patient = login("patient");
        JsonNode draft = createV2(patient, unsupportedVisit());
        mvc.perform(post("/api/v2/visits/{id}/analyze-complaint", draft.get("id").asText())
                .header("Authorization", bearer(patient)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("INVALID"))
            .andExpect(jsonPath("$.errorCode").value("AI_INVALID_JSON_OBJECT"));
    }

    @Test
    void complaintOnlyDraftCanBeAnalyzedButCannotBeSubmittedWithoutTags() throws Exception {
        when(aiClient.structureComplaint(anyString(), anyList(), anyList(), any())).thenReturn(new AiComplaintResult(
            "患者自述腹部疼痛。",
            java.util.List.of(new ComplaintTagView("ABDOMINAL_PAIN", "腹痛", "消化系统", "ai_extracted", 0.96, "肚子痛", "proposed")),
            new ComplaintFactsView("", "", "腹部", "", java.util.List.of(), java.util.List.of(), java.util.List.of(), ""),
            java.util.List.of(), java.util.List.of(), java.util.List.of(),
            "fake", "fake-v1", 8L, "AI 仅用于整理患者表述，不能替代医生诊断。"));
        String patient = login("patient");
        JsonNode draft = createV2(patient,
            "{\"primarySymptomCode\":null,\"chiefComplaint\":\"我肚子痛\",\"freeText\":\"\",\"symptomReports\":[]}");
        String id = draft.get("id").asText();
        mvc.perform(post("/api/v2/visits/{id}/analyze-complaint", id).header("Authorization", bearer(patient)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.tags[0].code").value("ABDOMINAL_PAIN"));
        mvc.perform(post("/api/v2/visits/{id}/submit", id).header("Authorization", bearer(patient))
                .header("Idempotency-Key", "complaint-only-001"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("VISIT_SYMPTOMS_REQUIRED"));
    }

    @Test
    void pgvectorKnowledgeSearchRemainsProtected() throws Exception {
        assertThat(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM pg_extension WHERE extname='vector')", Boolean.class)).isTrue();
        seedKnowledgeFixture();
        String body = "{\"symptomCodes\":[\"CHEST_PAIN\"],\"query\":\"胸痛\",\"limit\":3}";
        mvc.perform(post("/internal/v1/knowledge/search").header("X-Internal-Token", "wrong-token").contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isUnauthorized());
        mvc.perform(post("/internal/v1/knowledge/search").header("X-Internal-Token", "change-me-local-only").contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isOk()).andExpect(jsonPath("$[0].chunkId").value("chunk-test-red-flag-001"));
    }

    private JsonNode createV2(String token, String body) throws Exception {
        return mapper.readTree(mvc.perform(post("/api/v2/visits").header("Authorization", bearer(token)).contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("DRAFT")).andReturn().getResponse().getContentAsString());
    }

    private JsonNode submitV2(String token, String id, String key) throws Exception {
        return mapper.readTree(mvc.perform(post("/api/v2/visits/{id}/submit", id).header("Authorization", bearer(token)).header("Idempotency-Key", key))
            .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("PENDING_REVIEW")).andReturn().getResponse().getContentAsString());
    }

    private String unsupportedVisit() {
        return "{\"primarySymptomCode\":\"HEADACHE\",\"chiefComplaint\":\"今天头痛\",\"freeText\":\"希望记录具体情况\",\"symptomReports\":[{\"symptomCode\":\"HEADACHE\",\"source\":\"USER_SELECTED\",\"onsetRange\":\"TODAY\",\"course\":\"INTERMITTENT\",\"currentStatus\":\"PRESENT\",\"activityImpact\":\"NEEDS_REST\",\"answers\":[]}]}";
    }

    private String emergencyMixedVisit() {
        return "{\"primarySymptomCode\":\"CHEST_PAIN\",\"chiefComplaint\":\"现在胸口不舒服并喘不上气\",\"freeText\":\"同时头痛\",\"symptomReports\":[" +
            "{\"symptomCode\":\"CHEST_PAIN\",\"source\":\"USER_SELECTED\",\"onsetRange\":\"JUST_NOW\",\"course\":\"CONTINUOUS\",\"currentStatus\":\"PRESENT\",\"activityImpact\":\"UNABLE_NORMAL_ACTIVITY\",\"answers\":[{\"questionId\":\"chest.current\",\"selectedOptions\":[\"YES\"]}]}," +
            "{\"symptomCode\":\"DYSPNEA\",\"source\":\"USER_SELECTED\",\"onsetRange\":\"JUST_NOW\",\"course\":\"CONTINUOUS\",\"currentStatus\":\"PRESENT\",\"activityImpact\":\"UNABLE_NORMAL_ACTIVITY\",\"answers\":[{\"questionId\":\"dyspnea.current\",\"selectedOptions\":[\"YES\"]}]}," +
            "{\"symptomCode\":\"HEADACHE\",\"source\":\"USER_SELECTED\",\"onsetRange\":\"TODAY\",\"course\":\"INTERMITTENT\",\"currentStatus\":\"PRESENT\",\"activityImpact\":\"UNKNOWN\",\"answers\":[]}]}";
    }

    private void seedKnowledgeFixture() {
        UUID guidelineId = UUID.randomUUID(), versionId = UUID.randomUUID(), chunkId = UUID.randomUUID();
        UUID unrelatedChunkId = UUID.randomUUID(), sameTopicBackgroundId = UUID.randomUUID();
        jdbc.update("INSERT INTO guidelines(id,guideline_id,title,publisher,source_url,license_note) VALUES (?,?,?,?,?,?)", guidelineId, "test-red-flags", "测试资料", "测试发布方", "https://example.org/test-source", "仅用于自动化测试");
        jdbc.update("INSERT INTO guideline_versions(id,guideline_id,version_id,version_label,language,object_key,sha256,active) VALUES (?,?,?,?,?,?,?,TRUE)", versionId, guidelineId, "test-red-flags-v1", "v1", "zh-CN", "test/v1.md", "0".repeat(64));
        jdbc.update("INSERT INTO guideline_chunks(id,version_id,chunk_id,section_name,topics,content,embedding) VALUES (?,?,?,?,?,?,CAST(? AS vector))", chunkId, versionId, "chunk-test-red-flag-001", "胸痛红旗", "CHEST_PAIN", "胸痛相关内容需人工评估", unitVector());
        jdbc.update("INSERT INTO guideline_chunks(id,version_id,chunk_id,section_name,topics,content,embedding) VALUES (?,?,?,?,?,?,CAST(? AS vector))", unrelatedChunkId, versionId, "chunk-test-headache-000", "头痛资料", "HEADACHE", "与本次胸痛查询无关的头痛内容", unitVector());
        jdbc.update("INSERT INTO guideline_chunks(id,version_id,chunk_id,section_name,topics,content,embedding) VALUES (?,?,?,?,?,?,CAST(? AS vector))", sameTopicBackgroundId, versionId, "chunk-test-background-000", "项目背景", "CHEST_PAIN", "全球公共卫生项目背景说明", unitVector());
    }

    private String unitVector() { return IntStream.range(0, 64).mapToObj(i -> i == 0 ? "1" : "0").collect(Collectors.joining(",", "[", "]")); }
    private String login(String username) throws Exception {
        String body = mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content("{\"username\":\"" + username + "\",\"password\":\"Demo123!\"}"))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return mapper.readTree(body).get("accessToken").asText();
    }
    private String bearer(String token) { return "Bearer " + token; }
}
