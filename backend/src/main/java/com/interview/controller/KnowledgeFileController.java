package com.interview.controller;

import com.interview.common.Result;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api")
public class KnowledgeFileController {
    private static final String UPLOAD_DISABLED_MESSAGE =
            "当前版本不支持应用内文档上传，请使用本机题库维护 skill 生成 JSON 导入包";
    private static final String CONVERSION_DISABLED_MESSAGE =
            "当前版本不支持应用内文档转换，请使用 JSON 导入包";

    @PostMapping("/knowledge-bases/{knowledgeBaseId}/files")
    public ResponseEntity<Result<String>> upload(@PathVariable Long knowledgeBaseId,
                                                 @RequestParam("file") MultipartFile file) {
        return ResponseEntity.status(HttpStatus.GONE)
                .body(Result.error(HttpStatus.GONE.value(), UPLOAD_DISABLED_MESSAGE));
    }

    @GetMapping(value = "/knowledge-files/{sourceFileId}/markdown", produces = "text/markdown;charset=UTF-8")
    public ResponseEntity<String> readMarkdown(@PathVariable Long sourceFileId) {
        return ResponseEntity.status(HttpStatus.GONE).body(CONVERSION_DISABLED_MESSAGE);
    }

    @GetMapping("/knowledge-files/{sourceFileId}/original")
    public ResponseEntity<Result<String>> downloadOriginal(@PathVariable Long sourceFileId) {
        return ResponseEntity.status(HttpStatus.GONE)
                .body(Result.error(HttpStatus.GONE.value(), UPLOAD_DISABLED_MESSAGE));
    }
}
