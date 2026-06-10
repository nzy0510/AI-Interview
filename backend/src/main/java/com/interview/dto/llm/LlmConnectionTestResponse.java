package com.interview.dto.llm;

import lombok.Data;

@Data
public class LlmConnectionTestResponse {
    private Boolean success;

    private String message;

    private Long latencyMs;
}
