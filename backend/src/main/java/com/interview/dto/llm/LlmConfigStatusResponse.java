package com.interview.dto.llm;

import lombok.Data;

@Data
public class LlmConfigStatusResponse {
    private Boolean configured;

    private Long activeConfigId;

    private String provider;

    private String displayName;

    private String modelName;
}
