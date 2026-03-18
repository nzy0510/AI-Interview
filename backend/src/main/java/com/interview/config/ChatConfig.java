package com.interview.config;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.AllMiniLmL6V2EmbeddingModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

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

    @Bean
    public OpenAiStreamingChatModel streamingChatLanguageModel() {
        if ("your_api_key_here".equals(apiKey) || apiKey == null || apiKey.isEmpty()) {
            System.err.println("CRITICAL: DeepSeek API Key is missing! Please set 'langchain4j.open-ai.chat-model.api-key' in application.yml");
        }
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
                .temperature(0.3) // Lower temperature for more objective evaluation
                .timeout(Duration.ofSeconds(60))
                .build();
    }

    /**
     * Local Embedding Model for RAG
     * // 本地嵌入模型 Bean（AllMiniLmL6V2，纯本地运算，不调用远程 API）
     */
    @Bean
    public EmbeddingModel embeddingModel() {
        return new AllMiniLmL6V2EmbeddingModel();
    }

    /**
     * In-Memory Vector Store for RAG
     * // 内存级向量数据库 Bean（InMemoryEmbeddingStore）
     */
    @Bean
    public EmbeddingStore<TextSegment> embeddingStore() {
        return new InMemoryEmbeddingStore<>();
    }
}
