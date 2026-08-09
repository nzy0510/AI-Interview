package com.interview.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.interview.entity.AppJob;
import com.interview.mapper.AppJobMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.regex.Pattern;

@Service
public class AppJobService {

    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_RUNNING = "RUNNING";
    public static final String STATUS_COMPLETED = "COMPLETED";
    public static final String STATUS_FAILED = "FAILED";
    private static final String SCOPE_PUBLIC = "PUBLIC";
    private static final Duration RUNNING_LOCK_EXTENSION = Duration.ofMinutes(15);

    private static final Pattern SENSITIVE_PATTERN = Pattern.compile(
            "(?i)(Authorization\\s*[:=]\\s*\\S+|Bearer\\s+\\S+|api_key\\s*[:=]\\s*\\S+|sk-[A-Za-z0-9_-]+)"
    );

    private final AppJobMapper appJobMapper;

    public AppJobService(AppJobMapper appJobMapper) {
        this.appJobMapper = appJobMapper;
    }

    @Transactional
    public AppJob createPendingJob(AppJob job) {
        job.setStatus(STATUS_PENDING);
        job.setStage(job.getStage() == null ? "PENDING" : job.getStage());
        job.setProgress(job.getProgress() == null ? 0 : job.getProgress());
        job.setRetryable(false);
        appJobMapper.insert(job);
        return job;
    }

    @Transactional
    public AppJob claimPendingJob(Long jobId, String workerId, Duration lockTtl) {
        int updated = appJobMapper.update(null, new UpdateWrapper<AppJob>()
                .eq("id", jobId)
                .eq("status", STATUS_PENDING)
                .set("status", STATUS_RUNNING)
                .set("claimed_by", workerId)
                .set("locked_until", LocalDateTime.now().plus(lockTtl)));
        if (updated != 1) {
            return null;
        }
        return appJobMapper.selectById(jobId);
    }

    @Transactional
    public boolean extendRunningJobLease(Long jobId, String executionToken, Duration lockTtl) {
        if (executionToken == null || executionToken.isBlank() || lockTtl == null
                || lockTtl.isZero() || lockTtl.isNegative()) {
            return false;
        }
        int updated = appJobMapper.update(null, new UpdateWrapper<AppJob>()
                .eq("id", jobId)
                .eq("status", STATUS_RUNNING)
                .eq("claimed_by", executionToken)
                .set("locked_until", LocalDateTime.now().plus(lockTtl)));
        return updated == 1;
    }

    @Transactional
    public void updateRunningJob(Long jobId, String workerId, String stage, int progress) {
        if (workerId == null || workerId.isBlank()) {
            throw new IllegalStateException("运行中作业缺少执行令牌");
        }
        UpdateWrapper<AppJob> update = new UpdateWrapper<AppJob>()
                .eq("id", jobId)
                .eq("status", STATUS_RUNNING)
                .eq("claimed_by", workerId)
                .set("stage", stage)
                .set("progress", progress)
                .set("locked_until", LocalDateTime.now().plus(RUNNING_LOCK_EXTENSION));
        if (appJobMapper.update(null, update) != 1) {
            throw new IllegalStateException("作业执行权已失效");
        }
    }

    @Transactional
    public void completeJob(Long jobId, String resultJson) {
        appJobMapper.update(null, new UpdateWrapper<AppJob>()
                .eq("id", jobId)
                .eq("status", STATUS_RUNNING)
                .set("status", STATUS_COMPLETED)
                .set("progress", 100)
                .set("result_json", resultJson)
                .set("claimed_by", null)
                .set("locked_until", null));
    }

    @Transactional
    public void completeJob(Long jobId, String workerId, String resultJson) {
        UpdateWrapper<AppJob> update = new UpdateWrapper<AppJob>()
                .eq("id", jobId)
                .eq("status", STATUS_RUNNING)
                .set("status", STATUS_COMPLETED)
                .set("progress", 100)
                .set("result_json", resultJson)
                .set("claimed_by", null)
                .set("locked_until", null);
        if (workerId != null) {
            update.eq("claimed_by", workerId);
        }
        if (appJobMapper.update(null, update) != 1) {
            throw new IllegalStateException("作业执行权已失效，不能完成作业");
        }
    }

    @Transactional
    public void failJob(Long jobId, String errorMessage) {
        failJob(jobId, null, errorMessage, true);
    }

    @Transactional
    public void failJob(Long jobId, String failedStage, String errorMessage, boolean retryable) {
        appJobMapper.update(null, new UpdateWrapper<AppJob>()
                .eq("id", jobId)
                .eq("status", STATUS_RUNNING)
                .set("status", STATUS_FAILED)
                .set("progress", 100)
                .set("failed_stage", failedStage)
                .set("error_message", sanitizeErrorMessage(errorMessage))
                .set("retryable", retryable)
                .set("claimed_by", null)
                .set("locked_until", null));
    }

    @Transactional
    public void failJob(Long jobId, String workerId, String failedStage, String errorMessage, boolean retryable) {
        UpdateWrapper<AppJob> update = new UpdateWrapper<AppJob>()
                .eq("id", jobId)
                .eq("status", STATUS_RUNNING)
                .set("status", STATUS_FAILED)
                .set("progress", 100)
                .set("failed_stage", failedStage)
                .set("error_message", sanitizeErrorMessage(errorMessage))
                .set("retryable", retryable)
                .set("claimed_by", null)
                .set("locked_until", null);
        if (workerId != null) {
            update.eq("claimed_by", workerId);
        }
        if (appJobMapper.update(null, update) != 1) {
            throw new IllegalStateException("作业执行权已失效，不能标记失败");
        }
    }

    @Transactional
    public boolean retryJob(Long jobId, Long userId) {
        return retryJob(jobId, userId, false);
    }

    @Transactional
    public boolean retryJob(Long jobId, Long userId, boolean admin) {
        AppJob job = appJobMapper.selectById(jobId);
        if (job == null || !STATUS_FAILED.equals(job.getStatus())) {
            return false;
        }
        if (!Boolean.TRUE.equals(job.getRetryable())) {
            return false;
        }
        if (!canRetry(job, userId, admin)) {
            return false;
        }
        UpdateWrapper<AppJob> update = new UpdateWrapper<AppJob>()
                .eq("id", jobId)
                .eq("status", STATUS_FAILED)
                .eq("retryable", true)
                .set("status", STATUS_PENDING)
                .set("progress", 0)
                .set("result_json", null)
                .set("failed_stage", null)
                .set("error_message", null)
                .set("retryable", false)
                .set("claimed_by", null)
                .set("locked_until", null)
                .set("retry_count", job.getRetryCount() == null ? 1 : job.getRetryCount() + 1);
        if (job.getRetryCount() == null) {
            update.isNull("retry_count");
        } else {
            update.eq("retry_count", job.getRetryCount());
        }
        return appJobMapper.update(null, update) == 1;
    }

    @Transactional
    public List<AppJob> listPendingJobs() {
        return appJobMapper.selectList(new QueryWrapper<AppJob>()
                .eq("status", STATUS_PENDING)
                .orderByAsc("create_time", "id"));
    }

    @Transactional
    public int recoverExpiredRunningJobs() {
        LocalDateTime now = LocalDateTime.now();
        List<AppJob> jobs = appJobMapper.selectList(new QueryWrapper<AppJob>()
                .eq("status", STATUS_RUNNING)
                .lt("locked_until", now));
        int recovered = 0;
        for (AppJob job : jobs) {
            recovered += appJobMapper.update(null, new UpdateWrapper<AppJob>()
                    .eq("id", job.getId())
                    .eq("status", STATUS_RUNNING)
                    .lt("locked_until", now)
                    .set("status", STATUS_PENDING)
                    .set("claimed_by", null)
                    .set("locked_until", null));
        }
        return recovered;
    }

    private boolean canRetry(AppJob job, Long userId, boolean admin) {
        if (SCOPE_PUBLIC.equalsIgnoreCase(job.getScope())) {
            return admin;
        }
        return job.getOwnerUserId() != null && job.getOwnerUserId().equals(userId);
    }

    private String sanitizeErrorMessage(String errorMessage) {
        if (errorMessage == null) {
            return null;
        }
        return SENSITIVE_PATTERN.matcher(errorMessage).replaceAll("[REDACTED]");
    }
}
