package com.interview.controller;

import com.interview.common.Result;
import com.interview.dto.questionbank.QuestionBankAtomListItem;
import com.interview.dto.questionbank.QuestionBankAtomQueryRequest;
import com.interview.dto.questionbank.QuestionBankBatchDetailResponse;
import com.interview.dto.questionbank.QuestionBankBatchListItem;
import com.interview.dto.questionbank.QuestionBankBulkAtomRequest;
import com.interview.dto.questionbank.QuestionBankImportPreviewResponse;
import com.interview.dto.questionbank.QuestionBankImportRequest;
import com.interview.dto.questionbank.QuestionBankImportResult;
import com.interview.dto.questionbank.QuestionBankPageResponse;
import com.interview.dto.questionbank.QuestionBankSearchRequest;
import com.interview.dto.questionbank.QuestionBankSearchResult;
import com.interview.service.AdminGuardService;
import com.interview.service.questionbank.QuestionBankService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/admin/question-bank")
public class QuestionBankAdminController {

    private final QuestionBankService questionBankService;
    private final AdminGuardService adminGuardService;

    public QuestionBankAdminController(QuestionBankService questionBankService,
                                       AdminGuardService adminGuardService) {
        this.questionBankService = questionBankService;
        this.adminGuardService = adminGuardService;
    }

    @GetMapping("/categories")
    public Result<List<Map<String, Object>>> categories(HttpServletRequest servletRequest) {
        requireAdmin(servletRequest);
        return Result.success(questionBankService.listCategories());
    }

    @PostMapping("/atoms/search")
    public Result<QuestionBankPageResponse<QuestionBankAtomListItem>> listAtoms(@RequestBody QuestionBankAtomQueryRequest request,
                                                                                HttpServletRequest servletRequest) {
        requireAdmin(servletRequest);
        return Result.success(questionBankService.listAtoms(request));
    }

    @PostMapping("/atoms/archive")
    public Result<Map<String, Integer>> archiveAtoms(@RequestBody QuestionBankBulkAtomRequest request,
                                                     HttpServletRequest servletRequest) {
        requireAdmin(servletRequest);
        return Result.success(questionBankService.archiveAtoms(request.getAtomIds()));
    }

    @PostMapping("/atoms/publish")
    public Result<Map<String, Integer>> publishAtoms(@RequestBody QuestionBankBulkAtomRequest request,
                                                     HttpServletRequest servletRequest) {
        requireAdmin(servletRequest);
        return Result.success(questionBankService.publishAtoms(request.getAtomIds()));
    }

    @PostMapping("/atoms/reindex")
    public Result<Map<String, Integer>> reindexAtoms(@RequestBody QuestionBankBulkAtomRequest request,
                                                     HttpServletRequest servletRequest) {
        requireAdmin(servletRequest);
        return Result.success(questionBankService.reindexAtoms(request.getAtomIds()));
    }

    @GetMapping("/batches")
    public Result<QuestionBankPageResponse<QuestionBankBatchListItem>> listBatches(@RequestParam(defaultValue = "1") int page,
                                                                                   @RequestParam(defaultValue = "20") int size,
                                                                                   HttpServletRequest servletRequest) {
        requireAdmin(servletRequest);
        return Result.success(questionBankService.listBatches(page, size));
    }

    @GetMapping("/batches/{batchId}")
    public Result<QuestionBankBatchDetailResponse> getBatch(@PathVariable String batchId,
                                                            HttpServletRequest servletRequest) {
        requireAdmin(servletRequest);
        return Result.success(questionBankService.getBatchDetail(batchId));
    }

    @PostMapping("/batches/{batchId}/archive")
    public Result<Map<String, Integer>> archiveBatch(@PathVariable String batchId,
                                                     HttpServletRequest servletRequest) {
        requireAdmin(servletRequest);
        return Result.success(questionBankService.archiveBatch(batchId));
    }

    @PostMapping("/import/validate")
    public Result<QuestionBankImportPreviewResponse> validateImport(@RequestBody QuestionBankImportRequest request,
                                                                    HttpServletRequest servletRequest) {
        requireAdmin(servletRequest);
        return Result.success(questionBankService.previewImport(request));
    }

    @PostMapping("/import/dry-run")
    public Result<Map<String, Object>> dryRunImport(@RequestBody QuestionBankImportRequest request,
                                                    HttpServletRequest servletRequest) {
        requireAdmin(servletRequest);
        request.setMode("DRY_RUN");
        QuestionBankImportPreviewResponse preview = questionBankService.previewImport(request);
        QuestionBankImportResult result = questionBankService.importBatch(request);
        return Result.success(Map.of("preview", preview, "result", result));
    }

    @PostMapping("/import/publish")
    public Result<Map<String, Object>> publishImport(@RequestBody QuestionBankImportRequest request,
                                                     HttpServletRequest servletRequest) {
        requireAdmin(servletRequest);
        request.setMode("AUTO_PUBLISH");
        QuestionBankImportPreviewResponse preview = questionBankService.previewImport(request);
        QuestionBankImportResult result = questionBankService.importBatch(request);
        return Result.success(Map.of("preview", preview, "result", result));
    }

    @PostMapping("/reindex/unsynced")
    public Result<Map<String, Integer>> reindexUnsynced(HttpServletRequest servletRequest) {
        requireAdmin(servletRequest);
        return Result.success(questionBankService.reindexUnsyncedPublishedAtomResult());
    }

    @PostMapping("/reindex/all")
    public Result<Map<String, Integer>> reindexAll(HttpServletRequest servletRequest) {
        requireAdmin(servletRequest);
        return Result.success(questionBankService.reindexAllPublishedAtomResult());
    }

    @PostMapping("/search-preview")
    public Result<List<QuestionBankSearchResult>> searchPreview(@RequestBody QuestionBankSearchRequest request,
                                                                HttpServletRequest servletRequest) {
        requireAdmin(servletRequest);
        return Result.success(questionBankService.search(request));
    }

    private void requireAdmin(HttpServletRequest request) {
        adminGuardService.requireAdmin(request);
    }
}
