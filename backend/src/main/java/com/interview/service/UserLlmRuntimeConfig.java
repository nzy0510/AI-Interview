package com.interview.service;

public record UserLlmRuntimeConfig(
        Long configId,
        Long userId,
        String provider,
        String displayName,
        String baseUrl,
        String modelName,
        String apiKey,
        Double temperature
) {
}
