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

    AiJobStatus analyze(String runId, Visit visit, List<SymptomEntity> symptoms, RuleOutcome rule) {
        Map<String, Object> body = Map.of(
            "runId", runId, "visitId", visit.id.toString(), "ageBand", "ADULT",
            "chiefComplaint", visit.chiefComplaint, "freeText", visit.freeText,
            "symptoms", symptoms.stream().map(value -> Map.of(
                "codeSystem", value.codeSystem, "code", value.code, "name", value.name,
                "severity", value.severity, "onset", value.onset == null ? "" : value.onset)).toList(),
            "ruleUrgency", rule.urgency().name(), "ruleReasonCodes", rule.reasonCodes()
        );
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
}

record AiJobAccepted(String jobId, String runId, String status) {}
record AiJobStatus(String jobId, String runId, String status, AiResult result, String errorCode, Long durationMs) {}
record AiResult(String caseSummary, Urgency proposedUrgency, List<String> rationale, List<String> missingQuestions,
                List<AiCitation> citations, AiSafety safety, String disclaimer, String provider, String model,
                String outputHash, Map<String, String> versions, List<String> agentTrace) {}
record AiCitation(String guidelineId, String chunkId, String claimKey, String quote, String title, String section, String sourceUrl, String licenseNote) {}
record AiSafety(String decision, List<String> reasonCodes) {}
