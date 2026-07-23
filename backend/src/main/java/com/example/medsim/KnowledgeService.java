package com.example.medsim;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.StringJoiner;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
class KnowledgeService {
    private static final int DIMENSIONS = 64;
    private static final Pattern TOKENS = Pattern.compile("[A-Z0-9_]+|[\\p{IsHan}]");
    private final JdbcTemplate jdbc;
    private final MinioClient minio;
    private final ObjectMapper objectMapper;
    private final Resource corpusResource;
    private final String guidelinesBucket;
    private final String artifactsBucket;
    private final boolean seedEnabled;

    KnowledgeService(JdbcTemplate jdbc,
                     ObjectMapper objectMapper,
                     ResourceLoader resourceLoader,
                     @Value("${app.minio-endpoint}") String endpoint,
                     @Value("${app.minio-access-key}") String accessKey,
                     @Value("${app.minio-secret-key}") String secretKey,
                     @Value("${app.minio-guidelines-bucket}") String guidelinesBucket,
                     @Value("${app.minio-artifacts-bucket}") String artifactsBucket,
                     @Value("${app.knowledge-corpus:classpath:knowledge/official-corpus.json}") String corpusLocation,
                     @Value("${app.knowledge-seed-enabled}") boolean seedEnabled) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
        this.corpusResource = resourceLoader.getResource(corpusLocation);
        this.guidelinesBucket = guidelinesBucket;
        this.artifactsBucket = artifactsBucket;
        this.seedEnabled = seedEnabled;
        this.minio = MinioClient.builder().endpoint(endpoint).credentials(accessKey, secretKey).build();
    }

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    void initialize() {
        if (seedEnabled) reindexKnowledge();
    }

    @Transactional
    void reindexKnowledge() {
        ensureBucket(guidelinesBucket);
        ensureBucket(artifactsBucket);
        Corpus corpus = readCorpus();
        if (corpus.schemaVersion() != 1 || corpus.documents() == null || corpus.documents().isEmpty()) {
            throw new IllegalStateException("KNOWLEDGE_CORPUS_EMPTY_OR_UNSUPPORTED");
        }
        for (CorpusDocument document : corpus.documents()) ingest(document);
        ingestSafetyBaseline();
        jdbc.update("UPDATE guideline_versions SET active=FALSE WHERE retrieval_method='legacy-seed'");
    }

    List<KnowledgeChunkView> search(List<String> symptomCodes, String query, int limit) {
        int bounded = Math.max(1, Math.min(limit, 8));
        String topicText = symptomCodes == null ? "" : symptomCodes.stream()
            .filter(Objects::nonNull).map(value -> value.toUpperCase(Locale.ROOT))
            .filter(value -> value.matches("[A-Z0-9_]+"))
            .collect(java.util.stream.Collectors.joining(" "));
        String searchText = topicText + " " + query;
        String lexicalPattern = lexicalPattern(topicText);
        String vector = vectorLiteral(embed(searchText));
        return jdbc.query("""
            WITH candidates AS (
              SELECT g.guideline_id, v.version_id, c.chunk_id, g.title, c.section_name, c.content,
                     g.source_url, g.license_note,
                     CASE WHEN ? <> '' AND
                       regexp_split_to_array(upper(c.topics), '[[:space:]]+') &&
                       regexp_split_to_array(upper(?), '[[:space:]]+') THEN 0 ELSE 1 END AS topic_rank,
                     CASE WHEN ? <> '' AND
                       (c.section_name || ' ' || c.content) ~* ? THEN 0 ELSE 1 END AS lexical_rank,
                     c.embedding <=> CAST(? AS vector) AS distance
              FROM guideline_chunks c
              JOIN guideline_versions v ON v.id=c.version_id AND v.active=TRUE
              JOIN guidelines g ON g.id=v.guideline_id
            ), diversified AS (
              SELECT *, row_number() OVER (
                PARTITION BY guideline_id ORDER BY topic_rank, lexical_rank, distance, chunk_id
              ) AS source_rank
              FROM candidates
            )
            SELECT guideline_id, version_id, chunk_id, title, section_name, content,
                   source_url, license_note, 1 - distance AS score
            FROM diversified
            WHERE source_rank <= 2
            ORDER BY topic_rank, lexical_rank, distance, chunk_id
            LIMIT ?
            """, (rs, row) -> new KnowledgeChunkView(
            rs.getString("guideline_id"), rs.getString("version_id"), rs.getString("chunk_id"),
            rs.getString("title"), rs.getString("section_name"), rs.getString("content"),
            rs.getString("source_url"), rs.getString("license_note"), rs.getDouble("score")
        ), topicText, topicText, lexicalPattern, lexicalPattern, vector, bounded);
    }

    private String lexicalPattern(String topicText) {
        var values = new LinkedHashSet<String>();
        for (String code : topicText.split(" ")) switch (code) {
            case "CHEST_PAIN" -> values.add("chest|heart attack|angina|胸痛|胸口");
            case "DYSPNEA" -> values.add("dyspnea|breath|breathing|shortness|呼吸|气短|喘");
            case "SYNCOPE" -> values.add("syncope|faint|pass(ed)? out|晕厥|晕倒|失去意识");
            case "ALTERED_CONSCIOUSNESS" -> values.add("conscious|confus|unresponsive|意识|反应异常");
            case "HEADACHE" -> values.add("headache|head pain|头痛|头疼");
            case "DIZZINESS" -> values.add("dizz|vertigo|头晕|眩晕");
            case "FEVER" -> values.add("fever|temperature|发热|发烧");
            case "COUGH" -> values.add("cough|咳嗽");
            case "ABDOMINAL_PAIN" -> values.add("abdominal pain|stomach pain|腹痛|肚子痛");
            case "NAUSEA", "VOMITING" -> values.add("nausea|vomit|sick|恶心|呕吐");
            case "PALPITATIONS" -> values.add("palpitation|heart racing|心悸|心慌");
            default -> { }
        }
        return String.join("|", values);
    }

    boolean isActiveChunk(String chunkId) {
        Integer count = jdbc.queryForObject("""
            SELECT COUNT(*) FROM guideline_chunks c
            JOIN guideline_versions v ON v.id=c.version_id
            WHERE c.chunk_id=? AND v.active=TRUE
            """, Integer.class, chunkId);
        return count != null && count > 0;
    }

    List<GuidelineView> listGuidelines() {
        return jdbc.query("""
            SELECT g.guideline_id,g.title,g.publisher,g.source_url,g.license_note,
                   COALESCE(MAX(v.version_id) FILTER (WHERE v.active),'') active_version,
                   COUNT(c.id) FILTER (WHERE v.active) chunk_count,
                   COALESCE(MAX(v.object_key) FILTER (WHERE v.active),'') object_key,
                   MAX(v.fetched_at) FILTER (WHERE v.active) fetched_at,
                   COALESCE(MAX(v.sha256) FILTER (WHERE v.active),'') content_sha256,
                   COALESCE(MAX(v.source_status) FILTER (WHERE v.active),'') source_status,
                   COALESCE(MAX(v.retrieval_method) FILTER (WHERE v.active),'') retrieval_method
            FROM guidelines g LEFT JOIN guideline_versions v ON v.guideline_id=g.id
            LEFT JOIN guideline_chunks c ON c.version_id=v.id
            GROUP BY g.id ORDER BY g.guideline_id
            """, (rs, row) -> new GuidelineView(
            rs.getString("guideline_id"), rs.getString("title"), rs.getString("publisher"),
            rs.getString("source_url"), rs.getString("license_note"), rs.getString("active_version"),
            rs.getLong("chunk_count"), rs.getString("object_key"), rs.getObject("fetched_at", java.time.OffsetDateTime.class),
            rs.getString("content_sha256"), rs.getString("source_status"), rs.getString("retrieval_method")));
    }

    @Transactional
    void activateVersion(String guidelineId, String versionId) {
        UUID guideline = jdbc.query("SELECT id FROM guidelines WHERE guideline_id=?", rs -> rs.next() ? (UUID) rs.getObject(1) : null, guidelineId);
        if (guideline == null) throw ApiException.notFound();
        Integer exists = jdbc.queryForObject("SELECT COUNT(*) FROM guideline_versions WHERE guideline_id=? AND version_id=?", Integer.class, guideline, versionId);
        if (exists == null || exists == 0) throw ApiException.notFound();
        jdbc.update("UPDATE guideline_versions SET active=FALSE WHERE guideline_id=?", guideline);
        jdbc.update("UPDATE guideline_versions SET active=TRUE WHERE guideline_id=? AND version_id=?", guideline, versionId);
    }

    private Corpus readCorpus() {
        try (var input = corpusResource.getInputStream()) {
            return objectMapper.readValue(input, Corpus.class);
        } catch (Exception ex) {
            throw new IllegalStateException("KNOWLEDGE_CORPUS_READ_FAILED:" + corpusResource, ex);
        }
    }

    private void ingest(CorpusDocument document) {
        requireText(document.guidelineId(), "guidelineId");
        requireText(document.versionId(), "versionId");
        requireText(document.markdown(), "markdown");
        if (document.chunks() == null || document.chunks().isEmpty()) {
            throw new IllegalStateException("KNOWLEDGE_DOCUMENT_HAS_NO_CHUNKS:" + document.guidelineId());
        }
        byte[] bytes = document.markdown().getBytes(StandardCharsets.UTF_8);
        String actualHash = sha256(bytes);
        if (!actualHash.equalsIgnoreCase(document.contentSha256())) {
            throw new IllegalStateException("KNOWLEDGE_HASH_MISMATCH:" + document.guidelineId());
        }
        ingestDocument(document.guidelineId(), document.title(), document.publisher(), document.sourceUrl(),
            document.licenseNote(), document.versionId(), document.versionLabel(), document.language(),
            document.fetchedAt(), actualHash, document.retrievalMethod(), document.sourceStatus(),
            document.httpStatus(), document.etag(), document.lastModified(), bytes, document.chunks());
    }

    private void ingestSafetyBaseline() {
        String title = "Adult symptom red-flag controlled baseline";
        String sourceUrl = "https://example.org/medsim/controlled-baseline";
        String license = "Controlled safety baseline; not a clinical guideline and not a substitute for qualified review.";
        List<CorpusChunk> chunks = List.of(
            new CorpusChunk("chunk-red-flag-chest-pain-001", "Chest pain with danger signs",
                "CHEST_PAIN DYSPNEA SYNCOPE EMERGENCY",
                "Chest pain with breathing difficulty, fainting, or altered consciousness requires immediate qualified emergency assessment."),
            new CorpusChunk("chunk-red-flag-consciousness-001", "Altered consciousness",
                "ALTERED_CONSCIOUSNESS SYNCOPE EMERGENCY",
                "New altered consciousness or fainting is a high-priority red flag for qualified assessment; the automated system must not conclude that it is low risk."),
            new CorpusChunk("chunk-manual-review-001", "Manual review outside configured rules",
                "HEADACHE FATIGUE MANUAL_REVIEW UNCONFIGURED_RULE",
                "Symptoms outside configured deterministic rules require qualified manual review; automation must not label them routine or low risk.")
        );
        StringBuilder markdown = new StringBuilder("# ").append(title).append("\n\nSource: ").append(sourceUrl).append("\n\n").append(license).append("\n");
        for (CorpusChunk chunk : chunks) markdown.append("\n## ").append(chunk.section()).append("\n\n").append(chunk.content()).append("\n");
        byte[] bytes = markdown.toString().getBytes(StandardCharsets.UTF_8);
        ingestDocument("project-red-flags", title, "Medsim controlled baseline", sourceUrl, license,
            "project-safety-baseline-v3", "controlled-baseline-v3", "en", "2026-07-22T00:00:00Z",
            sha256(bytes), "bundled-safety-baseline", "CONTROLLED_BASELINE", 0, "", "", bytes, chunks);
    }

    private void ingestDocument(String guidelineId, String title, String publisher, String sourceUrl, String licenseNote,
                                String versionId, String versionLabel, String language, String fetchedAt, String contentHash,
                                String retrievalMethod, String sourceStatus, int httpStatus, String etag, String lastModified,
                                byte[] bytes, List<CorpusChunk> chunks) {
        UUID guidelineUuid = stableUuid("guideline:" + guidelineId);
        UUID versionUuid = stableUuid("version:" + versionId);
        String objectKey = guidelineId + "/" + versionId + ".md";
        putObject(objectKey, bytes);
        jdbc.update("""
            INSERT INTO guidelines(id,guideline_id,title,publisher,source_url,license_note)
            VALUES (?,?,?,?,?,?) ON CONFLICT(guideline_id) DO UPDATE SET
            title=EXCLUDED.title,publisher=EXCLUDED.publisher,source_url=EXCLUDED.source_url,license_note=EXCLUDED.license_note
            """, guidelineUuid, guidelineId, title, publisher, sourceUrl, licenseNote);
        jdbc.update("UPDATE guideline_versions SET active=FALSE WHERE guideline_id=?", guidelineUuid);
        jdbc.update("""
            INSERT INTO guideline_versions(id,guideline_id,version_id,version_label,language,object_key,sha256,active,
              fetched_at,retrieval_method,source_status,http_status,etag,last_modified)
            VALUES (?,?,?,?,?,?,?,TRUE,?,?,?,?,?,?) ON CONFLICT(version_id) DO UPDATE SET
            version_label=EXCLUDED.version_label,language=EXCLUDED.language,object_key=EXCLUDED.object_key,
            sha256=EXCLUDED.sha256,fetched_at=EXCLUDED.fetched_at,retrieval_method=EXCLUDED.retrieval_method,
            source_status=EXCLUDED.source_status,http_status=EXCLUDED.http_status,etag=EXCLUDED.etag,last_modified=EXCLUDED.last_modified
            """, versionUuid, guidelineUuid, versionId, versionLabel, language, objectKey, contentHash,
            Timestamp.from(Instant.parse(fetchedAt)), retrievalMethod, sourceStatus, httpStatus, emptyToNull(etag), emptyToNull(lastModified));
        jdbc.update("UPDATE guideline_versions SET active=TRUE WHERE id=?", versionUuid);
        jdbc.update("DELETE FROM guideline_chunks WHERE version_id=?", versionUuid);
        for (CorpusChunk chunk : chunks) {
            requireText(chunk.chunkId(), "chunkId");
            String embeddingText = chunk.topics() + " " + chunk.section() + " " + chunk.content();
            jdbc.update("""
                INSERT INTO guideline_chunks(id,version_id,chunk_id,section_name,topics,content,embedding)
                VALUES (?,?,?,?,?,?,CAST(? AS vector))
                ON CONFLICT(id) DO UPDATE SET version_id=EXCLUDED.version_id,chunk_id=EXCLUDED.chunk_id,
                  section_name=EXCLUDED.section_name,topics=EXCLUDED.topics,content=EXCLUDED.content,embedding=EXCLUDED.embedding
                """, stableUuid("chunk:" + chunk.chunkId()), versionUuid, chunk.chunkId(), chunk.section(),
                chunk.topics(), chunk.content(), vectorLiteral(embed(embeddingText)));
        }
    }

    private void requireText(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalStateException("KNOWLEDGE_FIELD_REQUIRED:" + field);
    }

    private String emptyToNull(String value) { return value == null || value.isBlank() ? null : value; }

    private void ensureBucket(String bucket) {
        try {
            if (!minio.bucketExists(BucketExistsArgs.builder().bucket(bucket).build())) {
                minio.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
            }
        } catch (Exception ex) {
            throw new IllegalStateException("MINIO_BUCKET_INIT_FAILED:" + bucket, ex);
        }
    }

    private void putObject(String objectKey, byte[] bytes) {
        try {
            minio.putObject(PutObjectArgs.builder().bucket(guidelinesBucket).object(objectKey)
                .contentType("text/markdown; charset=utf-8")
                .stream(new ByteArrayInputStream(bytes), bytes.length, -1).build());
        } catch (Exception ex) {
            throw new IllegalStateException("MINIO_GUIDELINE_UPLOAD_FAILED:" + objectKey, ex);
        }
    }

    private float[] embed(String text) {
        float[] values = new float[DIMENSIONS];
        var matcher = TOKENS.matcher(text.toUpperCase(Locale.ROOT));
        while (matcher.find()) {
            String token = matcher.group();
            int hash = token.hashCode();
            int index = Math.floorMod(hash, DIMENSIONS);
            values[index] += ((hash >>> 8) & 1) == 0 ? 1f : -1f;
        }
        double norm = 0;
        for (float value : values) norm += value * value;
        norm = Math.sqrt(norm);
        if (norm == 0) values[0] = 1f;
        else for (int i = 0; i < values.length; i++) values[i] /= (float) norm;
        return values;
    }

    private String vectorLiteral(float[] values) {
        StringJoiner joiner = new StringJoiner(",", "[", "]");
        for (float value : values) joiner.add(String.format(Locale.ROOT, "%.7f", value));
        return joiner.toString();
    }

    private UUID stableUuid(String value) { return UUID.nameUUIDFromBytes(value.getBytes(StandardCharsets.UTF_8)); }

    private String sha256(byte[] value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

    private record Corpus(int schemaVersion, List<CorpusDocument> documents) {}
    private record CorpusDocument(String guidelineId, String title, String publisher, String sourceUrl, String finalUrl,
                                  String licenseNote, String language, String versionId, String versionLabel,
                                  String fetchedAt, String contentSha256, String retrievalMethod, String sourceStatus,
                                  int httpStatus, String etag, String lastModified, List<String> topics,
                                  String markdown, List<CorpusChunk> chunks) {}
    private record CorpusChunk(String chunkId, String section, String topics, String content) {}
}
