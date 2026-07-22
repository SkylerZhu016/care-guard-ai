package cn.edu.medplatform.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

/** 调用 FastAPI AI 服务的 HTTP 客户端 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AiServiceClient {
    @Value("${app.ai-service-url}") private String baseUrl;
    @Value("${app.internal-token}") private String internalToken;

    private final RestTemplate restTemplate = new RestTemplate();

    public void startPipeline(Long runId, Long visitId) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Internal-Token", internalToken);
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(
                Map.of("runId", runId, "visitId", visitId), headers);
        try {
            restTemplate.postForEntity(baseUrl + "/internal/pipeline/start", entity, Map.class);
        } catch (Exception e) {
            log.error("启动 AI 流水线失败: {}", e.getMessage());
            throw new RuntimeException("AI 服务不可用", e);
        }
    }

    public void ingestDocument(Long documentId, String fileUrl) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Internal-Token", internalToken);
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(
                Map.of("documentId", documentId, "fileUrl", fileUrl), headers);
        restTemplate.postForEntity(baseUrl + "/internal/knowledge/ingest", entity, Map.class);
    }

    public Object search(String query, int topK) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Internal-Token", internalToken);
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(
                Map.of("query", query, "topK", topK), headers);
        return restTemplate.postForEntity(baseUrl + "/internal/search", entity, Object.class).getBody();
    }
}
