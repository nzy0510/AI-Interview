package com.interview.service.questionbank;

import com.interview.entity.KnowledgeSourceFile;

import java.time.LocalDateTime;

public record KnowledgeSourceFileResponse(Long id,
                                          Long knowledgeBaseId,
                                          String originalFilename,
                                          String contentType,
                                          Long fileSize,
                                          String status,
                                          String errorMessage,
                                          boolean hasMarkdown,
                                          LocalDateTime createTime,
                                          LocalDateTime updateTime) {
    static KnowledgeSourceFileResponse from(KnowledgeSourceFile sourceFile) {
        return new KnowledgeSourceFileResponse(
                sourceFile.getId(),
                sourceFile.getKnowledgeBaseId(),
                sourceFile.getOriginalFilename(),
                sourceFile.getContentType(),
                sourceFile.getFileSize(),
                sourceFile.getStatus(),
                sourceFile.getErrorMessage(),
                sourceFile.getMarkdownStorageKey() != null && !sourceFile.getMarkdownStorageKey().isBlank(),
                sourceFile.getCreateTime(),
                sourceFile.getUpdateTime()
        );
    }
}
