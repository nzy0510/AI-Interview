package com.interview.dto.job;

import com.interview.entity.AppJob;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class AppJobResponse {
    private Long id;
    private String jobType;
    private String scope;
    private Long ownerUserId;
    private String status;
    private String stage;
    private Integer progress;
    private String resultJson;
    private String failedStage;
    private String errorMessage;
    private Boolean retryable;
    private Integer retryCount;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    public static AppJobResponse from(AppJob job) {
        AppJobResponse response = new AppJobResponse();
        response.setId(job.getId());
        response.setJobType(job.getJobType());
        response.setScope(job.getScope());
        response.setOwnerUserId(job.getOwnerUserId());
        response.setStatus(job.getStatus());
        response.setStage(job.getStage());
        response.setProgress(job.getProgress());
        response.setResultJson(job.getResultJson());
        response.setFailedStage(job.getFailedStage());
        response.setErrorMessage(job.getErrorMessage());
        response.setRetryable(job.getRetryable());
        response.setRetryCount(job.getRetryCount());
        response.setCreateTime(job.getCreateTime());
        response.setUpdateTime(job.getUpdateTime());
        return response;
    }
}
