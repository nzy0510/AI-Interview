package com.interview.dto.llm;

import lombok.Data;

@Data
public class LlmConfigRequest {
    private String provider;

    private String displayName;

    private String baseUrl;

    private String modelName;

    private String apiKey;

    private Double temperature;

    private Boolean active;
}
