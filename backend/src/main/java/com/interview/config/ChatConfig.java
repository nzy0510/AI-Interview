package com.interview.config;

import com.interview.service.EvaluationGenerator;
import com.interview.service.SessionStore;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.AllMiniLmL6V2EmbeddingModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.RedisTemplate;

@Configuration
public class ChatConfig {

    @Value("${app.embedding.provider:all-minilm}")
    private String embeddingProvider = "all-minilm";

    @Value("${app.embedding.endpoint:}")
    private String embeddingEndpoint = "";

    @Value("${app.embedding.connect-timeout-ms:3000}")
    private int embeddingConnectTimeoutMs = 3000;

    @Value("${app.embedding.read-timeout-ms:10000}")
    private int embeddingReadTimeoutMs = 10000;

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
    public EvaluationGenerator evaluationGenerator(com.interview.service.UserLlmConfigService userLlmConfigService,
                                                   com.interview.service.UserLlmModelFactory userLlmModelFactory,
                                                   InterviewPrompts prompts,
                                                   com.interview.service.AppEventService appEventService) {
        return new EvaluationGenerator(userLlmConfigService, userLlmModelFactory, prompts, appEventService);
    }
}
