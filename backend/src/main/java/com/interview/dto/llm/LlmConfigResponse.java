package com.interview.dto.llm;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class LlmConfigResponse {
    private Long id;

    private String provider;

    private String displayName;

    private String baseUrl;

    private String modelName;

    private String apiKeyHint;

    private Double temperature;

    private Boolean active;

    private String lastTestStatus;

    private String lastTestMessage;

    private LocalDateTime lastTestTime;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
