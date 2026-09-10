package com.interview.config;

import com.interview.service.EvaluationGenerator;
import com.interview.service.SessionStore;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.AllMiniLmL6V2EmbeddingModel;
import dev.langchain4j.model.openai.OpenAiEmbeddingModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.RedisTemplate;

import java.time.Duration;

@Configuration
public class ChatConfig {

    @Value("${app.embedding.provider:all-minilm}")
    private String embeddingProvider = "all-minilm";

    @Value("${app.embedding.endpoint:}")
    private String embeddingEndpoint = "";

    @Value("${app.embedding.base-url:}")
    private String embeddingBaseUrl = "";

    @Value("${app.embedding.api-key:}")
    private String embeddingApiKey = "";

    @Value("${app.embedding.model:text-embedding-v4}")
    private String embeddingModelName = "text-embedding-v4";

    @Value("${app.embedding.dimensions:768}")
    private int embeddingDimensions = 768;

    @Value("${app.embedding.batch-size:10}")
    private int embeddingBatchSize = 10;

    @Value("${app.embedding.connect-timeout-ms:3000}")
    private int embeddingConnectTimeoutMs = 3000;

    @Value("${app.embedding.read-timeout-ms:10000}")
    private int embeddingReadTimeoutMs = 10000;

    @Bean
    public EmbeddingModel embeddingModel() {
        if ("openai-compatible".equalsIgnoreCase(embeddingProvider)) {
            if (embeddingBaseUrl == null || embeddingBaseUrl.isBlank()) {
                throw new IllegalStateException("app.embedding.base-url is required for openai-compatible embeddings");
            }
            if (embeddingApiKey == null || embeddingApiKey.isBlank() || "demo".equals(embeddingApiKey.trim())) {
                throw new IllegalStateException("app.embedding.api-key is required for openai-compatible embeddings");
            }
            if (embeddingModelName == null || embeddingModelName.isBlank() || embeddingReadTimeoutMs < 1) {
                throw new IllegalStateException("Embedding model and a positive read timeout are required");
            }
            return new OpenAiCompatibleEmbeddingModel(OpenAiEmbeddingModel.builder()
                    .baseUrl(embeddingBaseUrl.trim())
                    .apiKey(embeddingApiKey.trim())
                    .modelName(embeddingModelName.trim())
                    .dimensions(embeddingDimensions)
                    .timeout(Duration.ofMillis(embeddingReadTimeoutMs))
                    // LangChain4j 0.29.1 counts attempts here: 1 disables retries (0 is unbounded).
                    .maxRetries(1)
                    .logRequests(false)
                    .logResponses(false)
                    .build(), embeddingDimensions, embeddingBatchSize);
        }
        if ("http".equalsIgnoreCase(embeddingProvider)) {
            if (embeddingEndpoint == null || embeddingEndpoint.isBlank()) {
                throw new IllegalStateException("app.embedding.endpoint is required when app.embedding.provider=http");
            }
            return new HttpEmbeddingModel(embeddingEndpoint,
                    ExternalHttpClientFactory.create(embeddingConnectTimeoutMs, embeddingReadTimeoutMs));
        }
        if ("all-minilm".equalsIgnoreCase(embeddingProvider)) {
            return new AllMiniLmL6V2EmbeddingModel();
        }
        throw new IllegalStateException("Unsupported app.embedding.provider; use all-minilm, http or openai-compatible");
    }

    @Bean
    public SessionStore sessionStore(@Autowired(required = false) RedisTemplate<String, Object> redisTemplate) {
        return new SessionStore(redisTemplate);
    }

    @Bean
    public EvaluationGenerator evaluationGenerator(com.interview.service.UserLlmConfigService userLlmConfigService,
                                                   com.interview.service.UserLlmModelFactory userLlmModelFactory,
                                                   InterviewPrompts prompts,
                                                   com.interview.service.AppEventService appEventService) {
        return new EvaluationGenerator(userLlmConfigService, userLlmModelFactory, prompts, appEventService);
    }
}
