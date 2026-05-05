package com.interview.controller;

import com.interview.common.Result;
import com.interview.dto.questionbank.QuestionBankImportRequest;
import com.interview.dto.questionbank.QuestionBankImportResult;
import com.interview.dto.questionbank.QuestionBankSearchRequest;
import com.interview.dto.questionbank.QuestionBankSearchResult;
import com.interview.entity.KnowledgeAtom;
import com.interview.service.questionbank.QuestionBankService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/internal/question-bank")
public class QuestionBankInternalController {

    private final QuestionBankService questionBankService;

    @Value("${question-bank.admin-token:}")
    private String adminToken;

    public QuestionBankInternalController(QuestionBankService questionBankService) {
        this.questionBankService = questionBankService;
    }

    @PostMapping("/search")
    public Result<List<QuestionBankSearchResult>> search(@RequestBody QuestionBankSearchRequest request,
                                                         HttpServletRequest servletRequest) {
        requireAdmin(servletRequest);
        return Result.success(questionBankService.search(request));
    }

    @GetMapping("/atoms/{atomId}")
    public Result<KnowledgeAtom> getAtom(@PathVariable String atomId, HttpServletRequest servletRequest) {
        requireAdmin(servletRequest);
        return Result.success(questionBankService.getByAtomId(atomId));
    }

    @GetMapping("/categories")
    public Result<List<Map<String, Object>>> categories(HttpServletRequest servletRequest) {
        requireAdmin(servletRequest);
        return Result.success(questionBankService.listCategories());
    }

    @PostMapping("/import")
    public Result<QuestionBankImportResult> importBatch(@RequestBody QuestionBankImportRequest request,
                                                        HttpServletRequest servletRequest) {
        requireAdmin(servletRequest);
        return Result.success(questionBankService.importBatch(request));
    }

    @PostMapping("/reindex")
    public Result<Map<String, Integer>> reindex(HttpServletRequest servletRequest) {
        requireAdmin(servletRequest);
        int synced = questionBankService.reindexPublishedAtoms();
        return Result.success(Map.of("synced", synced));
    }

    private void requireAdmin(HttpServletRequest request) {
        if (adminToken == null || adminToken.isBlank()) return;
        String header = request.getHeader("X-Question-Bank-Token");
        if (header == null || header.isBlank()) {
            String auth = request.getHeader("Authorization");
            if (auth != null && auth.startsWith("Bearer ")) {
                header = auth.substring(7);
            }
        }
        if (!adminToken.equals(header)) {
            throw new RuntimeException("题库维护 token 无效");
        }
    }
}
