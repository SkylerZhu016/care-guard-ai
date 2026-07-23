package com.example.medsim;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;

@Component
class AiClient {
    private final RestClient client;
    private final String token;
    private final int timeoutSeconds;
    private final ObjectMapper mapper;

    AiClient(RestClient.Builder builder, @Value("${app.ai-base-url}") String baseUrl,
             @Value("${app.internal-service-token}") String token,
             @Value("${app.ai-job-timeout-seconds}") int timeoutSeconds, ObjectMapper mapper) {
        var requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(5_000); requestFactory.setReadTimeout((timeoutSeconds + 5) * 1_000);
        this.client = builder.baseUrl(baseUrl).requestFactory(requestFactory).build(); this.token = token; this.timeoutSeconds = timeoutSeconds; this.mapper = mapper;
    }

    AiJobStatus analyze(String runId, Visit visit, List<SymptomEntity> symptoms, RuleOutcome rule, AiCaseContext context) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("runId", runId); body.put("visitId", visit.id.toString());
        body.put("ageBand", context.profileSnapshot() == null ? "UNKNOWN" : context.profileSnapshot().ageBand());
        body.put("chiefComplaint", visit.chiefComplaint); body.put("freeText", visit.freeText);
        body.put("symptoms", symptoms.stream().map(value -> {
            Map<String,Object> report = new LinkedHashMap<>();
            report.put("codeSystem", value.codeSystem); report.put("code", value.code); report.put("name", value.name);
            report.put("supportLevel", value.supportLevel == null ? SupportLevel.RECORD_ONLY.name() : value.supportLevel.name());
            report.put("source", value.reportSource == null ? "LEGACY" : value.reportSource);
            report.put("onsetRange", value.onsetRange == null ? "UNKNOWN" : value.onsetRange);
            report.put("course", value.course == null ? "UNKNOWN" : value.course);
            report.put("currentStatus", value.currentStatus == null ? "UNKNOWN" : value.currentStatus);
            report.put("activityImpact", value.activityImpact == null ? "UNKNOWN" : value.activityImpact);
            report.put("answers", parseAnswers(value.answersJson));
            return report;
        }).toList());
        if (rule.urgency() != null) body.put("ruleUrgency", rule.urgency().name());
        body.put("ruleReasonCodes", rule.reasonCodes());
        body.put("coverageStatus", rule.coverageStatus().name()); body.put("assessmentStatus", rule.assessmentStatus().name());
        body.put("complaintStructure", context.complaintAnalysis());
        body.put("profileSnapshot", context.profileSnapshot());
        body.put("history", context.history());
        body.put("supplements", context.supplements());
        final String requestJson;
        try { requestJson = mapper.writeValueAsString(body); }
        catch (JsonProcessingException ex) { throw new IllegalStateException("AI_REQUEST_SERIALIZATION_FAILED", ex); }
        var accepted = client.post().uri("/internal/v1/analysis-jobs").contentType(MediaType.APPLICATION_JSON)
            .accept(MediaType.APPLICATION_JSON).header("X-Internal-Token", token).body(requestJson).retrieve().body(AiJobAccepted.class);
        if (accepted == null) throw new IllegalStateException("AI_EMPTY_ACCEPT_RESPONSE");
        Instant deadline = Instant.now().plus(Duration.ofSeconds(timeoutSeconds));
        while (Instant.now().isBefore(deadline)) {
            var job = client.get().uri("/internal/v1/jobs/{id}", accepted.jobId()).header("X-Internal-Token", token)
                .retrieve().body(AiJobStatus.class);
            if (job != null && List.of("SUCCEEDED", "BLOCKED", "FAILED", "TIMEOUT").contains(job.status())) return job;
            try { Thread.sleep(200); } catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); throw new IllegalStateException("AI_WAIT_INTERRUPTED"); }
        }
        throw new IllegalStateException("AI_TIMEOUT");
    }

    AiComplaintResult structureComplaint(String rawComplaint, List<ComplaintTagView> selectedTags,
                                         PatientProfileInput profile) {
        Map<String,Object> body = new LinkedHashMap<>();
        body.put("rawComplaint", rawComplaint);
        body.put("selectedTags", selectedTags == null ? List.of() : selectedTags);
        body.put("ageBand", profile == null ? "UNKNOWN" : profile.ageBand());
        var result = client.post().uri("/internal/v1/complaint-structure")
            .contentType(MediaType.APPLICATION_JSON).accept(MediaType.APPLICATION_JSON)
            .header("X-Internal-Token", token).body(body).retrieve().body(AiComplaintResult.class);
        if (result == null) throw new IllegalStateException("AI_COMPLAINT_EMPTY_RESPONSE");
        return result;
    }

    private Object parseAnswers(String value) {
        if (value == null || value.isBlank()) return List.of();
        try { return mapper.readTree(value); }
        catch (JsonProcessingException ignored) { return List.of(); }
    }
}

record AiJobAccepted(String jobId, String runId, String status) {}
record AiJobStatus(String jobId, String runId, String status, AiResult result, String errorCode, Long durationMs) {}
record AiResult(String caseSummary, Urgency proposedUrgency, List<String> rationale, List<String> missingQuestions,
                String structuredSummary, List<String> keyFindings, List<String> abnormalSignals,
                List<String> areasToRuleOut, List<String> recommendedAdditionalInformation,
                List<String> riskSignals, String evidenceSynthesis, List<String> uncertainties,
                List<String> clinicalThinkingPrompts,
                List<AiCitation> citations, AiSafety safety, String disclaimer, String provider, String model,
                String outputHash, Map<String, String> versions, List<String> agentTrace) {}
record AiCitation(String guidelineId, String chunkId, String claimKey, String quote, String title, String section, String sourceUrl, String licenseNote) {}
record AiSafety(String decision, List<String> reasonCodes) {}
record AiCaseContext(ComplaintAnalysisView complaintAnalysis, PatientProfileInput profileSnapshot,
                     List<Map<String,Object>> history, List<String> supplements) {}
record AiComplaintResult(String normalizedSummary, List<ComplaintTagView> extractedTags,
                         ComplaintFactsView structuredFacts, List<String> riskSignals,
                         List<String> missingQuestions, List<String> uncertainties,
                         String provider, String model, Long durationMs, String disclaimer) {}
