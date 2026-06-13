package com.interview.controller;

import com.interview.common.Result;
import com.interview.service.RequestUserResolver;
import com.interview.service.questionbank.KnowledgePositionCreateRequest;
import com.interview.service.questionbank.KnowledgePositionResponse;
import com.interview.service.questionbank.KnowledgeWorkspaceResponse;
import com.interview.service.questionbank.KnowledgeWorkspaceService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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

    @PostMapping("/positions/{positionId}/archive")
    public Result<Void> archivePrivatePosition(@PathVariable Long positionId,
                                               HttpServletRequest request) {
        workspaceService.archivePrivatePosition(currentUserId(request), positionId);
        return Result.success();
    }

    private Long currentUserId(HttpServletRequest request) {
        Long userId = requestUserResolver.resolveUserId(request);
        if (userId == null) {
            throw new RuntimeException("未登录：缺少用户身份");
        }
        return userId;
    }
}
