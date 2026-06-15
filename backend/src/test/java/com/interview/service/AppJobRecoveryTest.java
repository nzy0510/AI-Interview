package com.interview.service;

import com.interview.entity.AppJob;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.task.TaskExecutor;

import java.time.Duration;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("AppJobRecovery — 作业恢复与执行骨架")
class AppJobRecoveryTest {

    @Test
    @DisplayName("启动恢复入口委托 AppJobService 恢复过期 RUNNING 作业")
    void shouldRecoverExpiredRunningJobsOnStartup() {
        AppJobService appJobService = mock(AppJobService.class);
        AppJobDispatcher dispatcher = mock(AppJobDispatcher.class);
        TaskExecutor executor = Runnable::run;
        when(appJobService.recoverExpiredRunningJobs()).thenReturn(2);
        when(appJobService.listPendingJobs()).thenReturn(List.of());
        AppJobRecoveryService recoveryService = new AppJobRecoveryService(appJobService, dispatcher, executor);

        recoveryService.recoverOnStartup();

        verify(appJobService).recoverExpiredRunningJobs();
        verify(appJobService).listPendingJobs();
        verify(dispatcher, never()).dispatch(any());
    }

    @Test
    @DisplayName("启动恢复会声明并投递 PENDING 作业到本地执行器")
    void shouldClaimAndDispatchPendingJobsOnStartup() {
        AppJobService appJobService = mock(AppJobService.class);
        AppJobDispatcher dispatcher = mock(AppJobDispatcher.class);
        TaskExecutor executor = Runnable::run;
        AppJob pending = job("QUESTION_BANK_IMPORT");
        AppJob claimed = job("QUESTION_BANK_IMPORT");
        claimed.setClaimedBy("app-job-local");
        when(appJobService.listPendingJobs()).thenReturn(List.of(pending));
        when(appJobService.claimPendingJob(20L, "app-job-local", Duration.ofMinutes(15))).thenReturn(claimed);
        AppJobRecoveryService recoveryService = new AppJobRecoveryService(appJobService, dispatcher, executor);

        recoveryService.recoverOnStartup();

        verify(appJobService).recoverExpiredRunningJobs();
        verify(appJobService).claimPendingJob(20L, "app-job-local", Duration.ofMinutes(15));
        verify(dispatcher).dispatch(claimed);
    }

    @Test
    @DisplayName("没有 handler 的作业标记为可重试失败，不能误标完成")
    void shouldFailRetryableWhenHandlerIsMissing() {
        AppJobService appJobService = mock(AppJobService.class);
        AppJobDispatcher dispatcher = new AppJobDispatcher(appJobService, List.of());
        AppJob job = job("QUESTION_BANK_IMPORT");

        dispatcher.dispatch(job);

        verify(appJobService).failJob(20L, null, "DISPATCH",
                "No app job handler for type: QUESTION_BANK_IMPORT", true);
        verify(appJobService, never()).completeJob(20L, null);
    }

    @Test
    @DisplayName("handler 成功后作业标记为完成")
    void shouldCompleteWhenHandlerSucceeds() {
        AppJobService appJobService = mock(AppJobService.class);
        AppJobHandler handler = mock(AppJobHandler.class);
        when(handler.jobType()).thenReturn("QUESTION_BANK_IMPORT");
        AppJobDispatcher dispatcher = new AppJobDispatcher(appJobService, List.of(handler));
        AppJob job = job("QUESTION_BANK_IMPORT");

        dispatcher.dispatch(job);

        verify(handler).handle(job);
        verify(appJobService).completeJob(20L, null, null);
        verify(appJobService, never()).failJob(20L, null, "DISPATCH",
                "No app job handler for type: QUESTION_BANK_IMPORT", true);
    }

    @Test
    @DisplayName("handler 写入的结果会随完成状态保存")
    void shouldCompleteWithHandlerResultJson() {
        AppJobService appJobService = mock(AppJobService.class);
        AppJobHandler handler = new AppJobHandler() {
            @Override
            public String jobType() {
                return "TEST_JOB";
            }

            @Override
            public void handle(AppJob job) {
                job.setResultJson("{\"atomLimitReached\":true}");
            }
        };
        AppJobDispatcher dispatcher = new AppJobDispatcher(appJobService, List.of(handler));
        AppJob job = job("TEST_JOB");

        dispatcher.dispatch(job);

        verify(appJobService).completeJob(20L, null, "{\"atomLimitReached\":true}");
    }

    private AppJob job(String jobType) {
        AppJob job = new AppJob();
        job.setId(20L);
        job.setJobType(jobType);
        job.setStage("DISPATCH");
        return job;
    }
}
