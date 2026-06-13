package com.interview.service.questionbank;

import com.alibaba.fastjson2.JSON;
import com.interview.entity.AppJob;
import com.interview.entity.KnowledgeBase;
import com.interview.entity.KnowledgeSourceFile;
import com.interview.mapper.AppJobMapper;
import com.interview.mapper.KnowledgeBaseMapper;
import com.interview.mapper.KnowledgeSourceFileMapper;
import com.interview.service.AdminRoleService;
import com.interview.service.AppJobRecoveryService;
import com.interview.service.AppJobService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
public class KnowledgeFileImportService {

    private static final long MAX_FILE_SIZE_BYTES = 20L * 1024 * 1024;
    private static final String SCOPE_PUBLIC = "PUBLIC";
    private static final String SCOPE_PRIVATE = "PRIVATE";
    private static final Set<String> SUPPORTED_EXTENSIONS = Set.of("pdf", "docx", "md", "markdown", "txt");

    private final KnowledgeBaseMapper knowledgeBaseMapper;
    private final KnowledgeSourceFileMapper sourceFileMapper;
    private final AppJobMapper appJobMapper;
    private final AdminRoleService adminRoleService;
    private final AppJobRecoveryService appJobRecoveryService;
    private final FileStorageService fileStorageService;

    public KnowledgeFileImportService(KnowledgeBaseMapper knowledgeBaseMapper,
                                      KnowledgeSourceFileMapper sourceFileMapper,
                                      AppJobMapper appJobMapper,
                                      AdminRoleService adminRoleService,
                                      AppJobRecoveryService appJobRecoveryService,
                                      FileStorageService fileStorageService) {
        this.knowledgeBaseMapper = knowledgeBaseMapper;
        this.sourceFileMapper = sourceFileMapper;
        this.appJobMapper = appJobMapper;
        this.adminRoleService = adminRoleService;
        this.appJobRecoveryService = appJobRecoveryService;
        this.fileStorageService = fileStorageService;
    }

    @Transactional
    public KnowledgeFileUploadResponse upload(Long knowledgeBaseId, Long currentUserId, MultipartFile file) {
        if (currentUserId == null) {
            throw new RuntimeException("未登录：缺少用户身份");
        }
        KnowledgeBase knowledgeBase = knowledgeBaseMapper.selectById(knowledgeBaseId);
        if (knowledgeBase == null) {
            throw new RuntimeException("无权访问知识库");
        }
        boolean admin = adminRoleService.isAdmin(currentUserId);
        requireUploadAccess(knowledgeBase, currentUserId, admin);
        validateFile(file);

        FileStorageService.StoredFile stored = storeOriginal(file);
        KnowledgeSourceFile sourceFile = new KnowledgeSourceFile();
        sourceFile.setScope(knowledgeBase.getScope());
        sourceFile.setOwnerUserId(SCOPE_PUBLIC.equalsIgnoreCase(knowledgeBase.getScope()) ? null : knowledgeBase.getOwnerUserId());
        sourceFile.setPositionId(knowledgeBase.getPositionId());
        sourceFile.setKnowledgeBaseId(knowledgeBase.getId());
        sourceFile.setOriginalFilename(stored.filename());
        sourceFile.setContentType(stored.contentType());
        sourceFile.setFileSize(stored.size());
        sourceFile.setFileHash(stored.sha256());
        sourceFile.setStorageKey(stored.storageKey());
        sourceFile.setStatus("UPLOADED");
        sourceFile.setCreatedBy(currentUserId);
        sourceFileMapper.insert(sourceFile);

        AppJob job = new AppJob();
        job.setJobType(DocumentConversionJobHandler.JOB_TYPE);
        job.setScope(sourceFile.getScope());
        job.setOwnerUserId(sourceFile.getOwnerUserId());
        job.setPositionId(sourceFile.getPositionId());
        job.setKnowledgeBaseId(sourceFile.getKnowledgeBaseId());
        job.setSourceFileId(sourceFile.getId());
        job.setStatus(AppJobService.STATUS_PENDING);
        job.setStage("CONVERT");
        job.setProgress(0);
        job.setRetryable(false);
        job.setRetryCount(0);
        job.setCreatedBy(currentUserId);
        job.setPayloadJson(JSON.toJSONString(Map.of("sourceFileId", sourceFile.getId())));
        appJobMapper.insert(job);
        appJobRecoveryService.dispatchJob(job.getId());

        return new KnowledgeFileUploadResponse(sourceFile.getId(), job.getId(), sourceFile.getStatus());
    }

    private void requireUploadAccess(KnowledgeBase knowledgeBase, Long currentUserId, boolean admin) {
        String scope = knowledgeBase.getScope();
        if (SCOPE_PUBLIC.equalsIgnoreCase(scope)) {
            if (!admin) {
                throw new RuntimeException("无权访问知识库");
            }
            return;
        }
        if (SCOPE_PRIVATE.equalsIgnoreCase(scope)
                && (admin || currentUserId.equals(knowledgeBase.getOwnerUserId()))) {
            return;
        }
        throw new RuntimeException("无权访问知识库");
    }

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("文件不能为空");
        }
        if (file.getSize() > MAX_FILE_SIZE_BYTES) {
            throw new IllegalArgumentException("知识库文件不能超过 20MB");
        }
        String extension = extensionOf(file.getOriginalFilename());
        if (!SUPPORTED_EXTENSIONS.contains(extension)) {
            throw new IllegalArgumentException("仅支持 PDF、DOCX、Markdown/MD 和 TXT 文件");
        }
    }

    private FileStorageService.StoredFile storeOriginal(MultipartFile file) {
        try {
            return fileStorageService.store(file, "knowledge/originals");
        } catch (IOException e) {
            throw new RuntimeException("文件保存失败");
        }
    }

    private static String extensionOf(String filename) {
        if (filename == null || filename.isBlank()) {
            return "";
        }
        int dot = filename.lastIndexOf('.');
        if (dot < 0 || dot == filename.length() - 1) {
            return "";
        }
        return filename.substring(dot + 1).toLowerCase(Locale.ROOT);
    }
}
