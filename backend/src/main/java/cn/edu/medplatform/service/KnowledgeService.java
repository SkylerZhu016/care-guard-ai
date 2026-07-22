package cn.edu.medplatform.service;

import cn.edu.medplatform.entity.*;
import cn.edu.medplatform.repository.*;
import cn.edu.medplatform.common.BusinessException;
import cn.edu.medplatform.common.ErrorCode;
import cn.edu.medplatform.security.SecurityUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class KnowledgeService {
    private final KnowledgeDocumentRepository docRepo;
    private final UploadedFileRepository fileRepo;
    private final SystemConfigRepository configRepo;
    private final AuditService auditService;
    private final AiServiceClient aiClient;
    private final ObjectMapper om = new ObjectMapper();

    // MinIO 配置（简化：实际由 FileService 管理）
    @org.springframework.beans.factory.annotation.Value("${app.minio.endpoint}") private String minioEndpoint;
    @org.springframework.beans.factory.annotation.Value("${app.minio.bucket}") private String bucket;

    @Transactional
    public KnowledgeDocument upload(MultipartFile file, Map<String, String> metadata) {
        try {
            // 保存文件记录
            UploadedFile uf = new UploadedFile();
            uf.setFileName(file.getOriginalFilename());
            uf.setObjectKey("guidelines/" + System.currentTimeMillis() + "_" + file.getOriginalFilename());
            uf.setBucket(bucket);
            uf.setContentType(file.getContentType());
            uf.setSize(file.getSize());
            uf.setUploadedBy(SecurityUtils.getCurrentUserId());
            fileRepo.save(uf);

            // 创建文档记录
            KnowledgeDocument doc = new KnowledgeDocument();
            doc.setTitle(metadata.getOrDefault("title", file.getOriginalFilename()));
            doc.setOrg(metadata.get("org"));
            doc.setDocType(metadata.getOrDefault("docType", "GUIDELINE"));
            doc.setScope(metadata.get("scope"));
            doc.setSourceNote(metadata.getOrDefault("sourceNote", "教学整理自公开指南"));
            doc.setFileId(uf.getId());
            doc.setStatus("PENDING");
            doc.setUploadedBy(SecurityUtils.getCurrentUserId());
            docRepo.save(doc);

            // 触发 AI 摄取
            String fileUrl = minioEndpoint + "/" + bucket + "/" + uf.getObjectKey();
            try {
                aiClient.ingestDocument(doc.getId(), fileUrl);
            } catch (Exception e) {
                log.warn("AI 摄取触发失败，文档保持 PENDING: {}", e.getMessage());
            }

            auditService.logCurrent("GUIDELINE_UPLOAD", "KNOWLEDGE_DOCUMENT", String.valueOf(doc.getId()), doc.getTitle());
            return doc;
        } catch (Exception e) {
            throw new RuntimeException("上传失败: " + e.getMessage(), e);
        }
    }

    @Transactional
    public void onIngested(Long docId, Integer chunkCount, String status) {
        KnowledgeDocument doc = docRepo.findById(docId).orElseThrow();
        doc.setStatus(status != null ? status : "ENABLED");
        doc.setUpdatedAt(LocalDateTime.now());
        docRepo.save(doc);
        // 递增知识库版本
        bumpVersion("knowledge_version");
    }

    public Object search(String query, int topK) {
        return aiClient.search(query, topK);
    }

    @Transactional
    public void toggle(Long id, boolean enabled) {
        KnowledgeDocument doc = docRepo.findById(id).orElseThrow();
        doc.setStatus(enabled ? "ENABLED" : "DISABLED");
        docRepo.save(doc);
    }

    @Transactional
    public void delete(Long id) {
        KnowledgeDocument doc = docRepo.findById(id).orElseThrow();
        doc.setDeleted(true);
        doc.setStatus("DISABLED");
        docRepo.save(doc);
    }

    private void bumpVersion(String key) {
        SystemConfig cfg = configRepo.findByConfigKey(key);
        if (cfg != null) {
            try {
                String[] parts = cfg.getConfigValue().split("-");
                int num = Integer.parseInt(parts[parts.length - 1]) + 1;
                cfg.setConfigValue(parts[0] + "-" + num);
                configRepo.save(cfg);
            } catch (Exception e) {
                cfg.setConfigValue(cfg.getConfigValue() + "-1");
                configRepo.save(cfg);
            }
        }
    }
}
