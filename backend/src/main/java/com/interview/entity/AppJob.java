package com.interview.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("app_job")
public class AppJob {
    @TableId(type = IdType.AUTO)
    private Long id;

    private String jobType;
    private String scope;
    private Long ownerUserId;
    private Long positionId;
    private Long knowledgeBaseId;
    private Long sourceFileId;
    private Long recordId;
    private String status;
    private String stage;
    private Integer progress;
    private String payloadJson;
    private String resultJson;
    private String failedStage;
    private String errorMessage;
    private Boolean retryable;
    private Integer retryCount;
    private String claimedBy;
    private LocalDateTime lockedUntil;
    private Long createdBy;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
