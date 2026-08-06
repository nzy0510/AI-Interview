package com.interview.controller;

import com.interview.common.Result;
import com.interview.dto.questionbank.QuestionBankImportRequest;
import com.interview.dto.questionbank.QuestionBankImportResult;
import com.interview.dto.questionbank.build.QuestionBankBuildCandidateResponse;
import com.interview.dto.questionbank.build.QuestionBankBuildCandidateReviewRequest;
import com.interview.dto.questionbank.build.QuestionBankBuildResponse;
import com.interview.service.RequestUserResolver;
import com.interview.service.questionbank.build.QuestionBankBuildService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/knowledge-workspace/knowledge-bases/{knowledgeBaseId}/builds")
public class QuestionBankBuildController {
    private final QuestionBankBuildService buildService;
    private final RequestUserResolver requestUserResolver;

    public QuestionBankBuildController(QuestionBankBuildService buildService, RequestUserResolver requestUserResolver) {
        this.buildService = buildService;
        this.requestUserResolver = requestUserResolver;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result<QuestionBankBuildResponse> create(@PathVariable Long knowledgeBaseId,
                                                    @RequestParam("files") List<MultipartFile> files,
                                                    @RequestParam(value = "categories", required = false) List<String> categories,
                                                    HttpServletRequest request) {
        return Result.success(buildService.create(currentUserId(request), knowledgeBaseId, files, categories));
    }

    @GetMapping
    public Result<List<QuestionBankBuildResponse>> list(@PathVariable Long knowledgeBaseId, HttpServletRequest request) {
        return Result.success(buildService.list(currentUserId(request), knowledgeBaseId));
    }

    @GetMapping("/{buildId}")
    public Result<QuestionBankBuildResponse> detail(@PathVariable Long knowledgeBaseId,
                                                    @PathVariable Long buildId,
                                                    HttpServletRequest request) {
        return Result.success(buildService.detail(currentUserId(request), knowledgeBaseId, buildId));
    }

    @DeleteMapping("/{buildId}")
    public Result<Void> delete(@PathVariable Long knowledgeBaseId,
                               @PathVariable Long buildId,
                               HttpServletRequest request) {
        buildService.delete(currentUserId(request), knowledgeBaseId, buildId);
        return Result.success();
    }

    @GetMapping("/{buildId}/candidates")
    public Result<List<QuestionBankBuildCandidateResponse>> candidates(@PathVariable Long knowledgeBaseId,
                                                                       @PathVariable Long buildId,
                                                                       HttpServletRequest request) {
        return Result.success(buildService.candidates(currentUserId(request), knowledgeBaseId, buildId));
    }

    @PutMapping("/{buildId}/candidates/{candidateId}")
    public Result<QuestionBankBuildCandidateResponse> review(@PathVariable Long knowledgeBaseId,
                                                              @PathVariable Long buildId,
                                                              @PathVariable Long candidateId,
                                                              @RequestBody QuestionBankBuildCandidateReviewRequest body,
                                                              HttpServletRequest request) {
        return Result.success(buildService.review(currentUserId(request), knowledgeBaseId, buildId, candidateId, body));
    }

    @GetMapping("/{buildId}/package")
    public Result<QuestionBankImportRequest> packageFor(@PathVariable Long knowledgeBaseId,
                                                        @PathVariable Long buildId,
                                                        HttpServletRequest request) {
        return Result.success(buildService.packageFor(currentUserId(request), knowledgeBaseId, buildId));
    }

    @PostMapping("/{buildId}/import")
    public Result<QuestionBankImportResult> importBuild(@PathVariable Long knowledgeBaseId,
                                                        @PathVariable Long buildId,
                                                        HttpServletRequest request) {
        return Result.success(buildService.importBuild(currentUserId(request), knowledgeBaseId, buildId));
    }

    private Long currentUserId(HttpServletRequest request) {
        Long userId = requestUserResolver.resolveUserId(request);
        if (userId == null) throw new RuntimeException("未登录：缺少用户身份");
        return userId;
    }
}
