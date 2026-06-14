package com.interview.service;

import com.interview.entity.AppJob;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class AppJobDispatcher {

    private final AppJobService appJobService;
    private final Map<String, AppJobHandler> handlers = new HashMap<>();

    public AppJobDispatcher(AppJobService appJobService, List<AppJobHandler> handlers) {
        this.appJobService = appJobService;
        for (AppJobHandler handler : handlers) {
            this.handlers.put(handler.jobType(), handler);
        }
    }

    public void dispatch(AppJob job) {
        AppJobHandler handler = handlers.get(job.getJobType());
        if (handler == null) {
            appJobService.failJob(job.getId(), job.getClaimedBy(), job.getStage(),
                    "No app job handler for type: " + job.getJobType(), true);
            return;
        }
        try {
            handler.handle(job);
            appJobService.completeJob(job.getId(), job.getClaimedBy(), job.getResultJson());
        } catch (RuntimeException e) {
            appJobService.failJob(job.getId(), job.getClaimedBy(), job.getStage(), e.getMessage(), true);
        }
    }
}
