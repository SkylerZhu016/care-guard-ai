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
        jdbc.execute("TRUNCATE TABLE visit_supplements, patient_profiles, audit_logs, safety_alerts, followup_tasks, followup_plans, citations, agent_runs, triage_results, symptoms, visits, guideline_chunks, guideline_versions, guidelines CASCADE");
        when(aiClient.analyze(anyString(), any(), anyList(), any())).thenThrow(new IllegalStateException("AI_UNAVAILABLE_TEST"));
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
            .andExpect(status().isOk()).andExpect(jsonPath("$.version").value("intake-catalog-2026.07"))
            .andExpect(jsonPath("$.symptoms[0].questions").isArray());
        JsonNode submitted = submitV2(patient, createV2(patient, unsupportedVisit()).get("id").asText(), "queue-001");
        mvc.perform(get("/api/v2/clinician/visits").header("Authorization", bearer(clinician)))
            .andExpect(status().isOk()).andExpect(jsonPath("$[0].id").value(submitted.get("id").asText()));
        mvc.perform(get("/api/v2/clinician/visits").header("Authorization", bearer(patient)))
            .andExpect(status().isForbidden());
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
        return "{\"primarySymptomCode\":\"HEADACHE\",\"chiefComplaint\":\"今天头痛\",\"freeText\":\"希望记录具体情况\",\"symptomReports\":[{\"symptomCode\":\"HEADACHE\",\"source\":\"CATALOG\",\"onsetRange\":\"TODAY\",\"course\":\"INTERMITTENT\",\"currentStatus\":\"PRESENT\",\"activityImpact\":\"NEEDS_REST\",\"answers\":[]}]}";
    }

    private String emergencyMixedVisit() {
        return "{\"primarySymptomCode\":\"CHEST_PAIN\",\"chiefComplaint\":\"现在胸口不舒服并喘不上气\",\"freeText\":\"同时头痛\",\"symptomReports\":[" +
            "{\"symptomCode\":\"CHEST_PAIN\",\"source\":\"CATALOG\",\"onsetRange\":\"JUST_NOW\",\"course\":\"CONTINUOUS\",\"currentStatus\":\"PRESENT\",\"activityImpact\":\"UNABLE_NORMAL_ACTIVITY\",\"answers\":[{\"questionId\":\"chest.current\",\"selectedOptions\":[\"YES\"]}]}," +
            "{\"symptomCode\":\"DYSPNEA\",\"source\":\"CATALOG\",\"onsetRange\":\"JUST_NOW\",\"course\":\"CONTINUOUS\",\"currentStatus\":\"PRESENT\",\"activityImpact\":\"UNABLE_NORMAL_ACTIVITY\",\"answers\":[{\"questionId\":\"dyspnea.current\",\"selectedOptions\":[\"YES\"]}]}," +
            "{\"symptomCode\":\"HEADACHE\",\"source\":\"CATALOG\",\"onsetRange\":\"TODAY\",\"course\":\"INTERMITTENT\",\"currentStatus\":\"PRESENT\",\"activityImpact\":\"UNKNOWN\",\"answers\":[]}]}";
    }

    private void seedKnowledgeFixture() {
        UUID guidelineId = UUID.randomUUID(), versionId = UUID.randomUUID(), chunkId = UUID.randomUUID();
        jdbc.update("INSERT INTO guidelines(id,guideline_id,title,publisher,source_url,license_note) VALUES (?,?,?,?,?,?)", guidelineId, "test-red-flags", "测试资料", "测试发布方", "https://example.org/test-source", "仅用于自动化测试");
        jdbc.update("INSERT INTO guideline_versions(id,guideline_id,version_id,version_label,language,object_key,sha256,active) VALUES (?,?,?,?,?,?,?,TRUE)", versionId, guidelineId, "test-red-flags-v1", "v1", "zh-CN", "test/v1.md", "0".repeat(64));
        jdbc.update("INSERT INTO guideline_chunks(id,version_id,chunk_id,section_name,topics,content,embedding) VALUES (?,?,?,?,?,?,CAST(? AS vector))", chunkId, versionId, "chunk-test-red-flag-001", "胸痛红旗", "CHEST_PAIN", "胸痛相关内容需人工评估", unitVector());
    }

    private String unitVector() { return IntStream.range(0, 64).mapToObj(i -> i == 0 ? "1" : "0").collect(Collectors.joining(",", "[", "]")); }
    private String login(String username) throws Exception {
        String body = mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content("{\"username\":\"" + username + "\",\"password\":\"Demo123!\"}"))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return mapper.readTree(body).get("accessToken").asText();
    }
    private String bearer(String token) { return "Bearer " + token; }
}
