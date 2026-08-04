package com.interview.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.interview.common.Result;
import com.interview.dto.job.AppJobResponse;
import com.interview.entity.AppJob;
import com.interview.mapper.AppJobMapper;
import com.interview.service.AdminRoleService;
import com.interview.service.AppJobRecoveryService;
import com.interview.service.AppJobService;
import com.interview.service.RequestUserResolver;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/jobs")
public class AppJobController {

    private static final String SCOPE_PUBLIC = "PUBLIC";

    private final AppJobMapper appJobMapper;
    private final AppJobService appJobService;
    private final AppJobRecoveryService appJobRecoveryService;
    private final RequestUserResolver requestUserResolver;
    private final AdminRoleService adminRoleService;

    public AppJobController(AppJobMapper appJobMapper,
                            AppJobService appJobService,
                            AppJobRecoveryService appJobRecoveryService,
                            RequestUserResolver requestUserResolver,
                            AdminRoleService adminRoleService) {
        this.appJobMapper = appJobMapper;
        this.appJobService = appJobService;
        this.appJobRecoveryService = appJobRecoveryService;
        this.requestUserResolver = requestUserResolver;
        this.adminRoleService = adminRoleService;
    }

    @GetMapping
    public Result<List<AppJobResponse>> list(HttpServletRequest request) {
        Long userId = currentUserId(request);
        QueryWrapper<AppJob> query = new QueryWrapper<>();
        query.and(visible -> visible
                .eq("owner_user_id", userId)
                .or()
                .eq("scope", SCOPE_PUBLIC));
        query.orderByDesc("create_time", "id");
        return Result.success(appJobMapper.selectList(query).stream()
                .map(AppJobResponse::from)
                .toList());
    }

    @GetMapping("/{jobId}")
    public Result<AppJobResponse> detail(@PathVariable Long jobId,
                                          HttpServletRequest request) {
        Long userId = currentUserId(request);
        AppJob job = visibleJob(jobId, userId);
        return Result.success(AppJobResponse.from(job));
    }

    @PostMapping("/{jobId}/retry")
    public Result<Void> retry(@PathVariable Long jobId,
                              HttpServletRequest request) {
        Long userId = currentUserId(request);
        boolean admin = adminRoleService.isAdmin(userId);
        AppJob job = visibleJob(jobId, userId);
        if (!isOwnedByUser(job, userId) && !(admin && isPublic(job))) {
            throw new RuntimeException("无权访问作业或作业不可重试");
        }
        if (!appJobService.retryJob(jobId, userId, admin)) {
            throw new RuntimeException("无权访问作业或作业不可重试");
        }
        appJobRecoveryService.dispatchJob(jobId);
        return Result.success();
    }

    private AppJob visibleJob(Long jobId, Long userId) {
        AppJob job = appJobMapper.selectById(jobId);
        if (job == null) {
            throw new RuntimeException("无权访问作业");
        }
        if (!isVisibleToUser(job, userId)) {
            throw new RuntimeException("无权访问作业");
        }
        return job;
    }

    private boolean isVisibleToUser(AppJob job, Long userId) {
        if (isOwnedByUser(job, userId)) {
            return true;
        }
        return isPublic(job);
    }

    private boolean isPublic(AppJob job) {
        return SCOPE_PUBLIC.equalsIgnoreCase(job.getScope());
    }

    private boolean isOwnedByUser(AppJob job, Long userId) {
        return job.getOwnerUserId() != null && job.getOwnerUserId().equals(userId);
    }

    private Long currentUserId(HttpServletRequest request) {
        Long userId = requestUserResolver.resolveUserId(request);
        if (userId == null) {
            throw new RuntimeException("未登录：缺少用户身份");
        }
        return userId;
    }
}
