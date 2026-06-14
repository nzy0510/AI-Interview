package com.interview.controller;

import com.interview.common.Result;
import com.interview.service.RequestUserResolver;
import com.interview.service.questionbank.KnowledgeAtomBulkPublishResult;
import com.interview.service.questionbank.KnowledgeAtomGenerationResult;
import com.interview.service.questionbank.KnowledgeAtomJobService;
import com.interview.service.questionbank.KnowledgeAtomPatch;
import com.interview.service.questionbank.KnowledgeAtomResponse;
import com.interview.service.questionbank.KnowledgeAtomWorkflowService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api")
public class KnowledgeAtomController {

    private final KnowledgeAtomWorkflowService workflowService;
    private final KnowledgeAtomJobService jobService;
    private final RequestUserResolver requestUserResolver;

    public KnowledgeAtomController(KnowledgeAtomWorkflowService workflowService,
                                   KnowledgeAtomJobService jobService,
                                   RequestUserResolver requestUserResolver) {
        this.workflowService = workflowService;
        this.jobService = jobService;
        this.requestUserResolver = requestUserResolver;
    }

    @PostMapping("/knowledge-files/{sourceFileId}/atoms/generate")
    public Result<KnowledgeAtomGenerationResult> generate(@PathVariable Long sourceFileId,
                                                          HttpServletRequest request) {
        return Result.success(jobService.createGenerationJob(sourceFileId, currentUserId(request)));
    }

    @GetMapping("/knowledge-files/{sourceFileId}/atoms")
    public Result<List<KnowledgeAtomResponse>> list(@PathVariable Long sourceFileId,
                                                    HttpServletRequest request) {
        return Result.success(workflowService.listAtomsForSourceFile(sourceFileId, currentUserId(request)));
    }

    @PostMapping("/knowledge-files/{sourceFileId}/atoms")
    public Result<KnowledgeAtomResponse> createManual(@PathVariable Long sourceFileId,
                                                      @RequestBody KnowledgeAtomPatch patch,
                                                      HttpServletRequest request) {
        return Result.success(workflowService.createManualAtom(sourceFileId, currentUserId(request), patch));
    }

    @PostMapping("/knowledge-atoms/{atomId}/accept-patch")
    public Result<KnowledgeAtomResponse> acceptPatch(@PathVariable Long atomId,
                                                     HttpServletRequest request) {
        return Result.success(workflowService.acceptSuggestedPatch(atomId, currentUserId(request)));
    }

    @PutMapping("/knowledge-atoms/{atomId}")
    public Result<KnowledgeAtomResponse> update(@PathVariable Long atomId,
                                                @RequestBody KnowledgeAtomPatch patch,
                                                HttpServletRequest request) {
        return Result.success(workflowService.updateAtom(atomId, currentUserId(request), patch));
    }

    @PostMapping("/knowledge-atoms/{atomId}/publish")
    public Result<KnowledgeAtomResponse> publish(@PathVariable Long atomId,
                                                 HttpServletRequest request) {
        return Result.success(workflowService.publishAtom(atomId, currentUserId(request)));
    }

    @PostMapping("/knowledge-files/{sourceFileId}/atoms/publish")
    public Result<KnowledgeAtomBulkPublishResult> publishForSourceFile(@PathVariable Long sourceFileId,
                                                                       HttpServletRequest request) {
        return Result.success(workflowService.publishAtomsForSourceFile(sourceFileId, currentUserId(request)));
    }

    private Long currentUserId(HttpServletRequest request) {
        Long userId = requestUserResolver.resolveUserId(request);
        if (userId == null) {
            throw new RuntimeException("未登录：缺少用户身份");
        }
        return userId;
    }
}
