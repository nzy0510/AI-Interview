package com.interview.dto.llm;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class LlmProviderPresetResponse {
    private String provider;

    private String displayName;

    private String baseUrl;

    private String modelName;

    private Double temperature;
}
