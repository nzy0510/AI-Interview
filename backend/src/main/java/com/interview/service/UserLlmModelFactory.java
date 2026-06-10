package com.interview.service;

import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
public class UserLlmModelFactory {

    public OpenAiChatModel createChatModel(UserLlmRuntimeConfig config) {
        return OpenAiChatModel.builder()
                .apiKey(config.apiKey())
                .baseUrl(config.baseUrl())
                .modelName(config.modelName())
                .temperature(config.temperature())
                .timeout(Duration.ofSeconds(60))
                .build();
    }

    public OpenAiStreamingChatModel createStreamingChatModel(UserLlmRuntimeConfig config) {
        return OpenAiStreamingChatModel.builder()
                .apiKey(config.apiKey())
                .baseUrl(config.baseUrl())
                .modelName(config.modelName())
                .temperature(config.temperature())
                .timeout(Duration.ofSeconds(60))
                .build();
    }
}
