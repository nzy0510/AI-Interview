package com.interview.service;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.event.EventListener;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.ScheduledFuture;

@Service
public class AppJobRecoveryService {

    private static final String WORKER_ID = "app-job-local";
    private static final Duration LOCK_TTL = Duration.ofMinutes(15);
    private static final Duration HEARTBEAT_INTERVAL = Duration.ofMinutes(1);

    private final AppJobService appJobService;
    private final AppJobDispatcher appJobDispatcher;
    private final TaskExecutor appJobTaskExecutor;
    private final TaskScheduler appJobLeaseScheduler;

    public AppJobRecoveryService(AppJobService appJobService,
                                 AppJobDispatcher appJobDispatcher,
                                 @Qualifier("appJobTaskExecutor") TaskExecutor appJobTaskExecutor,
                                 @Qualifier("appJobLeaseScheduler") TaskScheduler appJobLeaseScheduler) {
        this.appJobService = appJobService;
        this.appJobDispatcher = appJobDispatcher;
        this.appJobTaskExecutor = appJobTaskExecutor;
        this.appJobLeaseScheduler = appJobLeaseScheduler;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void recoverOnStartup() {
        recoverExpiredRunningJobs();
        dispatchPendingJobs();
    }

    @Scheduled(fixedDelayString = "${app.jobs.recovery-interval-ms:60000}")
    public void recoverPeriodically() {
        recoverExpiredRunningJobs();
        dispatchPendingJobs();
    }

    public int recoverExpiredRunningJobs() {
        return appJobService.recoverExpiredRunningJobs();
    }

    public void dispatchPendingJobs() {
        appJobService.listPendingJobs().forEach(job ->
                appJobTaskExecutor.execute(() -> claimAndDispatch(job.getId())));
    }

    public void dispatchJob(Long jobId) {
        appJobTaskExecutor.execute(() -> claimAndDispatch(jobId));
    }

    private void claimAndDispatch(Long jobId) {
        String executionToken = WORKER_ID + ":" + UUID.randomUUID();
        var claimed = appJobService.claimPendingJob(jobId, executionToken, LOCK_TTL);
        if (claimed != null && executionToken.equals(claimed.getClaimedBy())) {
            ScheduledFuture<?> heartbeat = appJobLeaseScheduler.scheduleAtFixedRate(
                    () -> renewLease(jobId, executionToken),
                    HEARTBEAT_INTERVAL);
            try {
                appJobDispatcher.dispatch(claimed);
            } finally {
                if (heartbeat != null) heartbeat.cancel(false);
            }
        }
    }

    private void renewLease(Long jobId, String executionToken) {
        try {
            appJobService.extendRunningJobLease(jobId, executionToken, LOCK_TTL);
        } catch (RuntimeException ignored) {
            // A transient database failure must not cancel all later heartbeat attempts.
        }
    }
}
