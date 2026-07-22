package com.example.medsim;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class KnowledgeCorpusTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void officialCorpusIsSubstantialAuditableAndInternallyConsistent() throws Exception {
        try (InputStream input = getClass().getResourceAsStream("/knowledge/official-corpus.json")) {
            assertThat(input).isNotNull();
            JsonNode corpus = mapper.readTree(input);
            assertThat(corpus.path("schemaVersion").asInt()).isEqualTo(1);
            assertThat(corpus.path("documentCount").asInt()).isGreaterThanOrEqualTo(20);
            assertThat(corpus.path("chunkCount").asInt()).isGreaterThanOrEqualTo(100);

            Set<String> chunkIds = new HashSet<>();
            for (JsonNode document : corpus.path("documents")) {
                assertThat(document.path("sourceUrl").asText()).startsWith("https://");
                assertThat(document.path("sourceStatus").asText()).isEqualTo("FETCHED");
                assertThat(document.path("fetchedAt").asText()).endsWith("Z");
                assertThat(document.path("licenseNote").asText()).isNotBlank();
                String actual = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(document.path("markdown").asText().getBytes(StandardCharsets.UTF_8)));
                assertThat(document.path("contentSha256").asText()).isEqualTo(actual);
                for (JsonNode chunk : document.path("chunks")) {
                    assertThat(chunk.path("content").asText()).isNotBlank();
                    assertThat(chunkIds.add(chunk.path("chunkId").asText())).isTrue();
                }
            }
            assertThat(chunkIds).hasSize(corpus.path("chunkCount").asInt());
        }
    }
}
