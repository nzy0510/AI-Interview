package com.interview.controller;

import com.interview.common.Result;
import com.interview.dto.questionbank.QuestionBankAtomListItem;
import com.interview.dto.questionbank.QuestionBankAtomQueryRequest;
import com.interview.dto.questionbank.QuestionBankBulkAtomRequest;
import com.interview.dto.questionbank.QuestionBankImportPreviewResponse;
import com.interview.dto.questionbank.QuestionBankImportRequest;
import com.interview.dto.questionbank.QuestionBankImportResult;
import com.interview.dto.questionbank.QuestionBankPageResponse;
import com.interview.service.RequestUserResolver;
import com.interview.service.questionbank.KnowledgePositionCreateRequest;
import com.interview.service.questionbank.KnowledgePositionResponse;
import com.interview.service.questionbank.KnowledgeWorkspaceResponse;
import com.interview.service.questionbank.KnowledgeWorkspaceService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/knowledge-workspace")
public class KnowledgeWorkspaceController {

    private final KnowledgeWorkspaceService workspaceService;
    private final RequestUserResolver requestUserResolver;

    public KnowledgeWorkspaceController(KnowledgeWorkspaceService workspaceService,
                                        RequestUserResolver requestUserResolver) {
        this.workspaceService = workspaceService;
        this.requestUserResolver = requestUserResolver;
    }

    @GetMapping("/positions")
    public Result<KnowledgeWorkspaceResponse> listPositions(HttpServletRequest request) {
        return Result.success(workspaceService.listWorkspace(currentUserId(request)));
    }

    @PostMapping("/positions")
    public Result<KnowledgePositionResponse> createPrivatePosition(@RequestBody KnowledgePositionCreateRequest body,
                                                                   HttpServletRequest request) {
        return Result.success(workspaceService.createPrivatePosition(currentUserId(request), body));
    }

    @DeleteMapping("/positions/{positionId}")
    public Result<Void> deletePrivatePosition(@PathVariable Long positionId,
                                               HttpServletRequest request) {
        workspaceService.deletePrivatePosition(currentUserId(request), positionId);
        return Result.success();
    }

    @PostMapping("/knowledge-bases/{knowledgeBaseId}/import/validate")
    public Result<QuestionBankImportPreviewResponse> validateImport(@PathVariable Long knowledgeBaseId,
                                                                    @RequestBody QuestionBankImportRequest body,
                                                                    HttpServletRequest request) {
        return Result.success(workspaceService.previewImportPackage(currentUserId(request), knowledgeBaseId, body));
    }

    @PostMapping("/knowledge-bases/{knowledgeBaseId}/import")
    public Result<QuestionBankImportResult> importPackage(@PathVariable Long knowledgeBaseId,
                                                          @RequestBody QuestionBankImportRequest body,
                                                          HttpServletRequest request) {
        return Result.success(workspaceService.importPackage(currentUserId(request), knowledgeBaseId, body));
    }

    @PostMapping("/knowledge-bases/{knowledgeBaseId}/atoms/search")
    public Result<QuestionBankPageResponse<QuestionBankAtomListItem>> listAtoms(@PathVariable Long knowledgeBaseId,
                                                                                @RequestBody QuestionBankAtomQueryRequest body,
                                                                                HttpServletRequest request) {
        return Result.success(workspaceService.listAtoms(currentUserId(request), knowledgeBaseId, body));
    }

    @PostMapping("/knowledge-bases/{knowledgeBaseId}/atoms/archive")
    public Result<Map<String, Integer>> archiveAtoms(@PathVariable Long knowledgeBaseId,
                                                     @RequestBody QuestionBankBulkAtomRequest body,
                                                     HttpServletRequest request) {
        return Result.success(workspaceService.archiveAtoms(currentUserId(request), knowledgeBaseId, body));
    }

    @PostMapping("/knowledge-bases/{knowledgeBaseId}/atoms/publish")
    public Result<Map<String, Integer>> publishAtoms(@PathVariable Long knowledgeBaseId,
                                                     @RequestBody QuestionBankBulkAtomRequest body,
                                                     HttpServletRequest request) {
        return Result.success(workspaceService.publishAtoms(currentUserId(request), knowledgeBaseId, body));
    }

    @PostMapping("/knowledge-bases/{knowledgeBaseId}/atoms/publish-drafts")
    public Result<Map<String, Integer>> publishAllDrafts(@PathVariable Long knowledgeBaseId,
                                                         HttpServletRequest request) {
        return Result.success(workspaceService.publishAllDrafts(currentUserId(request), knowledgeBaseId));
    }

    @PostMapping("/knowledge-bases/{knowledgeBaseId}/atoms/reindex")
    public Result<Map<String, Integer>> reindexAtoms(@PathVariable Long knowledgeBaseId,
                                                     @RequestBody QuestionBankBulkAtomRequest body,
                                                     HttpServletRequest request) {
        return Result.success(workspaceService.reindexAtoms(currentUserId(request), knowledgeBaseId, body));
    }

    private Long currentUserId(HttpServletRequest request) {
        Long userId = requestUserResolver.resolveUserId(request);
        if (userId == null) {
            throw new RuntimeException("未登录：缺少用户身份");
        }
        return userId;
    }
}
