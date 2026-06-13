package com.interview.service;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.event.EventListener;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
public class AppJobRecoveryService {

    private static final String WORKER_ID = "app-job-local";
    private static final Duration LOCK_TTL = Duration.ofMinutes(15);

    private final AppJobService appJobService;
    private final AppJobDispatcher appJobDispatcher;
    private final TaskExecutor appJobTaskExecutor;

    public AppJobRecoveryService(AppJobService appJobService,
                                 AppJobDispatcher appJobDispatcher,
                                 @Qualifier("appJobTaskExecutor") TaskExecutor appJobTaskExecutor) {
        this.appJobService = appJobService;
        this.appJobDispatcher = appJobDispatcher;
        this.appJobTaskExecutor = appJobTaskExecutor;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void recoverOnStartup() {
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

    private void claimAndDispatch(Long jobId) {
        var claimed = appJobService.claimPendingJob(jobId, WORKER_ID, LOCK_TTL);
        if (claimed != null) {
            appJobDispatcher.dispatch(claimed);
        }
    }
}
