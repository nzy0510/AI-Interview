package com.interview.config;

import com.interview.service.EvaluationGenerator;
import com.interview.service.SessionStore;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.AllMiniLmL6V2EmbeddingModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.core.RedisTemplate;
import java.time.Duration;

@Configuration
public class ChatConfig {

    @Value("${langchain4j.open-ai.chat-model.api-key}")
    private String apiKey;

    @Value("${langchain4j.open-ai.chat-model.base-url}")
    private String baseUrl;

    @Value("${langchain4j.open-ai.chat-model.model-name}")
    private String modelName;

    @Value("${langchain4j.open-ai.chat-model.temperature}")
    private Double temperature;

    @Value("${app.embedding.provider:all-minilm}")
    private String embeddingProvider = "all-minilm";

    @Value("${app.embedding.endpoint:}")
    private String embeddingEndpoint = "";

    @Value("${app.embedding.connect-timeout-ms:3000}")
    private int embeddingConnectTimeoutMs = 3000;

    @Value("${app.embedding.read-timeout-ms:10000}")
    private int embeddingReadTimeoutMs = 10000;

    @Bean
    public OpenAiStreamingChatModel streamingChatLanguageModel() {
        return OpenAiStreamingChatModel.builder()
                .apiKey(apiKey)
                .baseUrl(baseUrl)
                .modelName(modelName)
                .temperature(temperature)
                .timeout(Duration.ofSeconds(60))
                .build();
    }

    @Bean
    @Primary
    public OpenAiChatModel chatLanguageModel() {
        return OpenAiChatModel.builder()
                .apiKey(apiKey)
                .baseUrl(baseUrl)
                .modelName(modelName)
                .temperature(0.3)
                .timeout(Duration.ofSeconds(60))
                .build();
    }

    @Bean
    public EmbeddingModel embeddingModel() {
        if ("http".equalsIgnoreCase(embeddingProvider)) {
            if (embeddingEndpoint == null || embeddingEndpoint.isBlank()) {
                throw new IllegalStateException("app.embedding.endpoint is required when app.embedding.provider=http");
            }
            return new HttpEmbeddingModel(embeddingEndpoint,
                    ExternalHttpClientFactory.create(embeddingConnectTimeoutMs, embeddingReadTimeoutMs));
        }
        return new AllMiniLmL6V2EmbeddingModel();
    }

    @Bean
    public SessionStore sessionStore(@Autowired(required = false) RedisTemplate<String, Object> redisTemplate) {
        return new SessionStore(redisTemplate);
    }

    @Bean
    public EvaluationGenerator evaluationGenerator(OpenAiChatModel chatModel,
                                                   InterviewPrompts prompts,
                                                   com.interview.service.AppEventService appEventService) {
        return new EvaluationGenerator(chatModel, prompts, appEventService);
    }
}
