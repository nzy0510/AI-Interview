package com.interview.dto.llm;

import lombok.Data;

@Data
public class LlmConnectionTestRequest {
    private Long configId;

    private String provider;

    private String baseUrl;

    private String modelName;

    private String apiKey;

    private Double temperature;
}
