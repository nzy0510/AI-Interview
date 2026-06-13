package com.interview.controller;

import com.interview.common.Result;
import com.interview.service.RequestUserResolver;
import com.interview.service.questionbank.KnowledgeFileDownload;
import com.interview.service.questionbank.KnowledgeFileImportService;
import com.interview.service.questionbank.KnowledgeFileReadService;
import com.interview.service.questionbank.KnowledgeFileUploadResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/api")
public class KnowledgeFileController {

    private final KnowledgeFileImportService importService;
    private final KnowledgeFileReadService readService;
    private final RequestUserResolver requestUserResolver;

    public KnowledgeFileController(KnowledgeFileImportService importService,
                                   KnowledgeFileReadService readService,
                                   RequestUserResolver requestUserResolver) {
        this.importService = importService;
        this.readService = readService;
        this.requestUserResolver = requestUserResolver;
    }

    @PostMapping("/knowledge-bases/{knowledgeBaseId}/files")
    public Result<KnowledgeFileUploadResponse> upload(@PathVariable Long knowledgeBaseId,
                                                      @RequestParam("file") MultipartFile file,
                                                      HttpServletRequest request) {
        return Result.success(importService.upload(knowledgeBaseId, currentUserId(request), file));
    }

    @GetMapping(value = "/knowledge-files/{sourceFileId}/markdown", produces = "text/markdown;charset=UTF-8")
    public ResponseEntity<String> readMarkdown(@PathVariable Long sourceFileId,
                                               HttpServletRequest request) {
        return ResponseEntity.ok(readService.readMarkdown(sourceFileId, currentUserId(request)));
    }

    @GetMapping("/knowledge-files/{sourceFileId}/original")
    public ResponseEntity<Resource> downloadOriginal(@PathVariable Long sourceFileId,
                                                     HttpServletRequest request) {
        KnowledgeFileDownload download = readService.originalFile(sourceFileId, currentUserId(request));
        String contentType = download.contentType() != null ? download.contentType() : MediaType.APPLICATION_OCTET_STREAM_VALUE;
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(contentType))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(download.filename(), StandardCharsets.UTF_8)
                        .build()
                        .toString())
                .body(download.resource());
    }

    private Long currentUserId(HttpServletRequest request) {
        Long userId = requestUserResolver.resolveUserId(request);
        if (userId == null) {
            throw new RuntimeException("未登录：缺少用户身份");
        }
        return userId;
    }
}
