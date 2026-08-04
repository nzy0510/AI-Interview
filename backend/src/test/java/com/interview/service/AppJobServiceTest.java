package com.interview.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.interview.entity.AppJob;
import com.interview.mapper.AppJobMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("AppJobService — 异步任务生命周期")
class AppJobServiceTest {

    private AppJobMapper appJobMapper;
    private AppJobService service;

    @BeforeEach
    void setUp() {
        appJobMapper = mock(AppJobMapper.class);
        service = new AppJobService(appJobMapper);
    }

    @Test
    @DisplayName("声明 PENDING 任务后写入锁字段，并可完成为 COMPLETED")
    void claimsPendingJobAndCompletesIt() {
        AppJob pending = new AppJob();
        pending.setId(10L);
        pending.setStatus("PENDING");
        pending.setProgress(0);
        when(appJobMapper.update(isNull(), any())).thenReturn(1);
        when(appJobMapper.selectById(10L)).thenReturn(pending);

        AppJob claimed = service.claimPendingJob(10L, "worker-1", Duration.ofMinutes(5));
        service.completeJob(claimed.getId(), "{\"ok\":true}");

        ArgumentCaptor<UpdateWrapper<AppJob>> runningUpdate = updateWrapperCaptor();
        verify(appJobMapper).selectById(10L);
        verify(appJobMapper, times(2)).update(isNull(), runningUpdate.capture());

        UpdateWrapper<AppJob> claimUpdate = runningUpdate.getAllValues().get(0);
        assertThat(claimUpdate.getSqlSegment()).contains("id", "status");
        assertThat(claimUpdate.getSqlSet()).contains("status", "claimed_by", "locked_until");
        assertThat(claimUpdate.getParamNameValuePairs().values()).contains("RUNNING", "worker-1");

        UpdateWrapper<AppJob> completeUpdate = runningUpdate.getAllValues().get(1);
        assertThat(completeUpdate.getSqlSegment()).contains("id", "status");
        assertThat(completeUpdate.getSqlSet())
                .contains("status", "progress", "result_json", "claimed_by", "locked_until");
        assertThat(completeUpdate.getParamNameValuePairs().values())
                .contains("COMPLETED", 100, "{\"ok\":true}");
    }

    @Test
    @DisplayName("worker 完成任务时带 claimed_by 条件，避免过期 worker 覆盖新领取状态")
    void completesOnlyTheClaimedRunningJob() {
        service.completeJob(10L, "worker-1", "{\"ok\":true}");

        ArgumentCaptor<UpdateWrapper<AppJob>> completedUpdate = updateWrapperCaptor();
        verify(appJobMapper).update(isNull(), completedUpdate.capture());

        assertThat(completedUpdate.getValue().getSqlSegment()).contains("id", "status", "claimed_by");
        assertThat(completedUpdate.getValue().getParamNameValuePairs().values())
                .contains("RUNNING", "worker-1", "COMPLETED");
    }

    @Test
    @DisplayName("并发声明失败时返回 null，避免多个 worker 同时领取同一任务")
    void returnsNullWhenConditionalClaimLosesRace() {
        AppJob pending = new AppJob();
        pending.setId(10L);
        pending.setStatus("PENDING");
        when(appJobMapper.update(isNull(), any())).thenReturn(0);

        AppJob claimed = service.claimPendingJob(10L, "worker-2", Duration.ofMinutes(5));

        assertThat(claimed).isNull();
        verify(appJobMapper, never()).selectById(10L);
    }

    @Test
    @DisplayName("失败任务会脱敏错误信息，并标记为 FAILED")
    void failsJobWithSanitizedError() {
        service.failJob(11L, "worker-1", "parse", "Bearer abc123 api_key=secret sk-test-123 Authorization: token", true);

        ArgumentCaptor<UpdateWrapper<AppJob>> failedUpdate = updateWrapperCaptor();
        verify(appJobMapper).update(isNull(), failedUpdate.capture());

        assertThat(failedUpdate.getValue().getSqlSegment()).contains("id", "status", "claimed_by");
        assertThat(failedUpdate.getValue().getSqlSet())
                .contains("status", "progress", "failed_stage", "error_message", "retryable", "claimed_by", "locked_until");
        assertThat(failedUpdate.getValue().getParamNameValuePairs().values())
                .contains("RUNNING", "worker-1", "FAILED", 100, "parse", true);
        assertThat(failedUpdate.getValue().getParamNameValuePairs().values())
                .anySatisfy(value -> assertThat(String.valueOf(value))
                        .contains("[REDACTED]")
                        .doesNotContain("Bearer", "api_key", "sk-test-123", "Authorization"));
    }

    @Test
    @DisplayName("人工重试仅允许本人重试可重试失败任务")
    void retriesOwnRetryableFailedJob() {
        AppJob failed = new AppJob();
        failed.setId(12L);
        failed.setStatus("FAILED");
        failed.setOwnerUserId(88L);
        failed.setRetryable(true);
        failed.setRetryCount(2);
        failed.setErrorMessage("old error");
        failed.setResultJson("{\"done\":false}");
        failed.setFailedStage("parse");
        when(appJobMapper.selectById(12L)).thenReturn(failed);

        assertThat(service.retryJob(12L, 88L)).isTrue();

        ArgumentCaptor<UpdateWrapper<AppJob>> retryUpdate = updateWrapperCaptor();
        verify(appJobMapper).selectById(12L);
        verify(appJobMapper).update(isNull(), retryUpdate.capture());

        assertThat(retryUpdate.getValue().getSqlSet())
                .contains("status", "progress", "result_json", "failed_stage", "error_message",
                        "retryable", "claimed_by", "locked_until", "retry_count");
        assertThat(retryUpdate.getValue().getParamNameValuePairs().values())
                .contains("PENDING", 0, false, 3);
    }

    @Test
    @DisplayName("普通用户不能重试 PUBLIC 作业，公共作业仅管理员可重试")
    void rejectsOrdinaryUserRetryingPublicJob() {
        AppJob publicJob = new AppJob();
        publicJob.setId(22L);
        publicJob.setStatus("FAILED");
        publicJob.setScope("PUBLIC");
        publicJob.setOwnerUserId(null);
        publicJob.setRetryable(true);
        when(appJobMapper.selectById(22L)).thenReturn(publicJob);

        assertThat(service.retryJob(22L, 88L, false)).isFalse();
        assertThat(service.retryJob(22L, 88L, true)).isTrue();

        verify(appJobMapper, times(1)).update(isNull(), any());
    }

    @Test
    @DisplayName("查询 PENDING 作业用于启动恢复和本地执行器投递")
    void listsPendingJobsForRecoveryDispatch() {
        AppJob pending = new AppJob();
        pending.setId(30L);
        pending.setStatus("PENDING");
        when(appJobMapper.selectList(any())).thenReturn(List.of(pending));

        assertThat(service.listPendingJobs()).containsExactly(pending);

        ArgumentCaptor<QueryWrapper<AppJob>> query = queryWrapperCaptor();
        verify(appJobMapper).selectList(query.capture());
        assertThat(query.getValue().getSqlSegment()).contains("status");
        assertThat(query.getValue().getParamNameValuePairs().values()).contains("PENDING");
    }

    @Test
    @DisplayName("拒绝重试他人、非 FAILED、或不可重试任务")
    void rejectsInvalidRetryRequests() {
        AppJob failed = new AppJob();
        failed.setId(13L);
        failed.setStatus("FAILED");
        failed.setOwnerUserId(88L);
        failed.setRetryable(true);

        AppJob notFailed = new AppJob();
        notFailed.setId(14L);
        notFailed.setStatus("RUNNING");

        AppJob notRetryable = new AppJob();
        notRetryable.setId(15L);
        notRetryable.setStatus("FAILED");
        notRetryable.setOwnerUserId(88L);
        notRetryable.setRetryable(false);

        when(appJobMapper.selectById(13L)).thenReturn(failed);
        when(appJobMapper.selectById(14L)).thenReturn(notFailed);
        when(appJobMapper.selectById(15L)).thenReturn(notRetryable);

        assertThat(service.retryJob(13L, 99L)).isFalse();
        assertThat(service.retryJob(13L, 99L, true)).isFalse();
        assertThat(service.retryJob(14L, 88L)).isFalse();
        assertThat(service.retryJob(15L, 88L)).isFalse();

        verify(appJobMapper, never()).update(any(), any());
    }

    @Test
    @DisplayName("仅恢复过期 RUNNING 任务，FAILED 不参与自动重试")
    void recoversExpiredRunningJobsButSkipsFailedJobs() {
        AppJob expiredRunning = new AppJob();
        expiredRunning.setId(16L);
        expiredRunning.setStatus("RUNNING");
        expiredRunning.setRetryable(true);
        expiredRunning.setRetryCount(1);
        expiredRunning.setLockedUntil(LocalDateTime.now().minusMinutes(1));

        when(appJobMapper.selectList(any())).thenReturn(List.of(expiredRunning));

        assertThat(service.recoverExpiredRunningJobs()).isEqualTo(1);

        ArgumentCaptor<QueryWrapper<AppJob>> query = queryWrapperCaptor();
        ArgumentCaptor<UpdateWrapper<AppJob>> recoveredUpdate = updateWrapperCaptor();
        verify(appJobMapper).selectList(query.capture());
        verify(appJobMapper, times(1)).update(isNull(), recoveredUpdate.capture());

        assertThat(query.getValue().getSqlSegment()).contains("status", "locked_until");
        assertThat(query.getValue().getParamNameValuePairs().values()).contains("RUNNING");
        assertThat(recoveredUpdate.getValue().getSqlSet())
                .contains("status", "claimed_by", "locked_until");
        assertThat(recoveredUpdate.getValue().getParamNameValuePairs().values())
                .contains("PENDING");
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static ArgumentCaptor<UpdateWrapper<AppJob>> updateWrapperCaptor() {
        return ArgumentCaptor.forClass((Class) UpdateWrapper.class);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static ArgumentCaptor<QueryWrapper<AppJob>> queryWrapperCaptor() {
        return ArgumentCaptor.forClass((Class) QueryWrapper.class);
    }
}
