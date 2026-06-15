package com.interview.controller;

import com.interview.common.Result;
import com.interview.service.RequestUserResolver;
import com.interview.service.questionbank.KnowledgeAtomPatch;
import com.interview.service.questionbank.KnowledgeAtomResponse;
import com.interview.service.questionbank.KnowledgeAtomWorkflowService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class KnowledgeAtomController {
    private static final String SOURCE_FILE_ATOMS_DISABLED_MESSAGE =
            "当前版本不支持应用内文档生成原子，请使用本机题库维护 skill 生成 JSON 导入包";

    private final KnowledgeAtomWorkflowService workflowService;
    private final RequestUserResolver requestUserResolver;

    public KnowledgeAtomController(KnowledgeAtomWorkflowService workflowService,
                                   RequestUserResolver requestUserResolver) {
        this.workflowService = workflowService;
        this.requestUserResolver = requestUserResolver;
    }

    @PostMapping("/knowledge-files/{sourceFileId}/atoms/generate")
    public ResponseEntity<Result<String>> generate(@PathVariable Long sourceFileId,
                                                   HttpServletRequest request) {
        return disabledSourceFileAtomFlow();
    }

    @GetMapping("/knowledge-files/{sourceFileId}/atoms")
    public ResponseEntity<Result<String>> list(@PathVariable Long sourceFileId,
                                               HttpServletRequest request) {
        return disabledSourceFileAtomFlow();
    }

    @PostMapping("/knowledge-files/{sourceFileId}/atoms")
    public ResponseEntity<Result<String>> createManual(@PathVariable Long sourceFileId,
                                                       @RequestBody KnowledgeAtomPatch patch,
                                                       HttpServletRequest request) {
        return disabledSourceFileAtomFlow();
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
    public ResponseEntity<Result<String>> publishForSourceFile(@PathVariable Long sourceFileId,
                                                               HttpServletRequest request) {
        return disabledSourceFileAtomFlow();
    }

    private <T> ResponseEntity<Result<T>> disabledSourceFileAtomFlow() {
        return ResponseEntity.status(HttpStatus.GONE)
                .body(Result.error(HttpStatus.GONE.value(), SOURCE_FILE_ATOMS_DISABLED_MESSAGE));
    }

    private Long currentUserId(HttpServletRequest request) {
        Long userId = requestUserResolver.resolveUserId(request);
        if (userId == null) {
            throw new RuntimeException("未登录：缺少用户身份");
        }
        return userId;
    }
}
