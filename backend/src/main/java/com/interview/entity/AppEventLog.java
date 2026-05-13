package com.interview.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Sanitized product and API event log.
 * Stores hashes and metadata only; request bodies, tokens and resume text are never persisted here.
 */
@Data
@TableName("app_event_log")
public class AppEventLog {
    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private String anonymousId;

    private String eventType;

    private String eventCategory;

    private String path;

    private String httpMethod;

    private Integer statusCode;

    private Boolean success;

    private String errorCode;

    private String errorMessage;

    private String ipHash;

    private String userAgentHash;

    private String requestId;

    private Long latencyMs;

    private String metadataJson;

    private LocalDateTime createTime;
}
