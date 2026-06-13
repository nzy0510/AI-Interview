package com.interview.service.questionbank;

import com.interview.entity.KnowledgeSourceFile;
import com.interview.mapper.KnowledgeSourceFileMapper;
import com.interview.service.AdminRoleService;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.io.IOException;

@Service
public class KnowledgeFileReadService {

    private static final String SCOPE_PUBLIC = "PUBLIC";

    private final KnowledgeSourceFileMapper sourceFileMapper;
    private final AdminRoleService adminRoleService;
    private final FileStorageService fileStorageService;

    public KnowledgeFileReadService(KnowledgeSourceFileMapper sourceFileMapper,
                                    AdminRoleService adminRoleService,
                                    FileStorageService fileStorageService) {
        this.sourceFileMapper = sourceFileMapper;
        this.adminRoleService = adminRoleService;
        this.fileStorageService = fileStorageService;
    }

    public String readMarkdown(Long sourceFileId, Long currentUserId) {
        KnowledgeSourceFile sourceFile = visibleSourceFile(sourceFileId, currentUserId);
        if (sourceFile.getMarkdownStorageKey() == null || sourceFile.getMarkdownStorageKey().isBlank()) {
            throw new IllegalArgumentException("文件尚未完成 Markdown 转换");
        }
        try {
            return fileStorageService.readText(sourceFile.getMarkdownStorageKey());
        } catch (IOException e) {
            throw new RuntimeException("读取 Markdown 文件失败");
        }
    }

    public KnowledgeFileDownload originalFile(Long sourceFileId, Long currentUserId) {
        KnowledgeSourceFile sourceFile = visibleSourceFile(sourceFileId, currentUserId);
        Resource resource = fileStorageService.loadAsResource(sourceFile.getStorageKey());
        return new KnowledgeFileDownload(resource, sourceFile.getOriginalFilename(), sourceFile.getContentType());
    }

    private KnowledgeSourceFile visibleSourceFile(Long sourceFileId, Long currentUserId) {
        if (currentUserId == null) {
            throw new RuntimeException("未登录：缺少用户身份");
        }
        KnowledgeSourceFile sourceFile = sourceFileMapper.selectById(sourceFileId);
        if (sourceFile == null) {
            throw new RuntimeException("无权访问文件");
        }
        if (SCOPE_PUBLIC.equalsIgnoreCase(sourceFile.getScope())) {
            return sourceFile;
        }
        if (sourceFile.getOwnerUserId() != null && sourceFile.getOwnerUserId().equals(currentUserId)) {
            return sourceFile;
        }
        if (adminRoleService.isAdmin(currentUserId)) {
            return sourceFile;
        }
        throw new RuntimeException("无权访问文件");
    }
}
