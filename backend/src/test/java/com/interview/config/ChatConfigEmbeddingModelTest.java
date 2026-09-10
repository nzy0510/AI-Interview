package com.interview.config;

import dev.langchain4j.model.embedding.AllMiniLmL6V2EmbeddingModel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("ChatConfig embedding model")
class ChatConfigEmbeddingModelTest {

    @Test
    void shouldRejectUnknownProviderInsteadOfLoadingALocalModel() {
        ChatConfig config = new ChatConfig();
        ReflectionTestUtils.setField(config, "embeddingProvider", "misspelled-provider");
        assertThatThrownBy(config::embeddingModel).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void shouldRequireCredentialsForOpenAiCompatibleEmbeddings() {
        ChatConfig config = new ChatConfig();
        ReflectionTestUtils.setField(config, "embeddingProvider", "openai-compatible");
        assertThatThrownBy(config::embeddingModel)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("app.embedding.base-url");
    }

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

    @Test
    @DisplayName("HTTP embedding client has bounded connect and read timeouts")
    void shouldConfigureEmbeddingHttpTimeouts() {
        ChatConfig config = new ChatConfig();
        ReflectionTestUtils.setField(config, "embeddingProvider", "http");
        ReflectionTestUtils.setField(config, "embeddingEndpoint", "http://embedding-service:8000/embed");

        HttpEmbeddingModel model = (HttpEmbeddingModel) config.embeddingModel();
        RestTemplate restTemplate = (RestTemplate) ReflectionTestUtils.getField(model, "restTemplate");
        assertThat(restTemplate).isNotNull();
        assertThat(restTemplate.getRequestFactory()).isInstanceOf(SimpleClientHttpRequestFactory.class);
        SimpleClientHttpRequestFactory factory =
                (SimpleClientHttpRequestFactory) restTemplate.getRequestFactory();
        assertThat((int) ReflectionTestUtils.getField(factory, "connectTimeout")).isPositive();
        assertThat((int) ReflectionTestUtils.getField(factory, "readTimeout")).isPositive();
    }
}
