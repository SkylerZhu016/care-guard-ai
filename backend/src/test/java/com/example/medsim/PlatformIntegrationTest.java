package com.example.medsim;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

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

    @BeforeEach void cleanBusinessData() {
        jdbc.execute("TRUNCATE TABLE audit_logs, safety_alerts, followup_tasks, followup_plans, citations, agent_runs, triage_results, symptoms, visits CASCADE");
    }

    @Test void completeRuleFirstFlowAndEnforceRbac() throws Exception {
        when(aiClient.analyze(anyString(), any(), anyList(), any())).thenThrow(new IllegalStateException("AI_UNAVAILABLE_TEST"));
        String patient = login("patient");
        String clinician = login("clinician");
        String admin = login("admin");

        String visitJson = mvc.perform(post("/api/v1/visits").header("Authorization", "Bearer " + patient)
                .contentType(MediaType.APPLICATION_JSON).content("""
                    {"chiefComplaint":"合成胸痛伴呼吸困难","freeText":"仅为教学模拟","symptoms":[
                      {"code":"CHEST_PAIN","name":"胸痛","severity":8,"onset":"1小时"},
                      {"code":"DYSPNEA","name":"呼吸困难","severity":6,"onset":"30分钟"}]}
                    """))
            .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("DRAFT"))
            .andReturn().getResponse().getContentAsString();
        String visitId = mapper.readTree(visitJson).get("id").asText();

        String submitted = mvc.perform(post("/api/v1/visits/{id}/submit", visitId)
                .header("Authorization", "Bearer " + patient).header("Idempotency-Key", "integration-flow-001"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("PENDING_REVIEW"))
            .andExpect(jsonPath("$.triage.ruleUrgency").value("EMERGENCY"))
            .andExpect(jsonPath("$.runs[0].status").value("FAILED"))
            .andReturn().getResponse().getContentAsString();

        mvc.perform(get("/api/v1/clinician/visits").header("Authorization", "Bearer " + patient))
            .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("AUTH_FORBIDDEN"));
        mvc.perform(get("/api/v1/clinician/visits").header("Authorization", "Bearer " + clinician))
            .andExpect(status().isOk()).andExpect(jsonPath("$[0].id").value(visitId));
        mvc.perform(get("/api/v1/admin/safety-alerts").header("Authorization", "Bearer " + admin))
            .andExpect(status().isOk()).andExpect(jsonPath("$[0].category").value("AI_UNAVAILABLE_OR_INVALID"));

        String triageId = mapper.readTree(submitted).get("triage").get("id").asText();
        mvc.perform(post("/api/v1/triage-results/{id}/review", triageId).header("Authorization", "Bearer " + clinician)
                .contentType(MediaType.APPLICATION_JSON).content("{\"decision\":\"ACCEPT\",\"reason\":\"已核对规则与合成信息\",\"finalUrgency\":\"ROUTINE\"}"))
            .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("TRIAGE_RULE_DOWNGRADE_FORBIDDEN"));
    }

    private String login(String username) throws Exception {
        String body = mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"" + username + "\",\"password\":\"Demo123!\"}"))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        JsonNode json = mapper.readTree(body); return json.get("accessToken").asText();
    }
}
