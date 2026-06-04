package com.interview.config;

import dev.langchain4j.model.embedding.AllMiniLmL6V2EmbeddingModel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ChatConfig embedding model")
class ChatConfigEmbeddingModelTest {

    @Test
    @DisplayName("uses local AllMiniLM embedding by default")
    void shouldUseAllMiniLmByDefault() {
        ChatConfig config = new ChatConfig();

        assertThat(config.embeddingModel()).isInstanceOf(AllMiniLmL6V2EmbeddingModel.class);
    }

    @Test
    @DisplayName("uses HTTP embedding provider when configured")
    void shouldUseHttpEmbeddingProviderWhenConfigured() {
        ChatConfig config = new ChatConfig();
        ReflectionTestUtils.setField(config, "embeddingProvider", "http");
        ReflectionTestUtils.setField(config, "embeddingEndpoint", "http://embedding-service:8000/embed");

        assertThat(config.embeddingModel()).isInstanceOf(HttpEmbeddingModel.class);
    }
}
