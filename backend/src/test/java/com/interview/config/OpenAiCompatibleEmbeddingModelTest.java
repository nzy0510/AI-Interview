package com.interview.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

@ExtendWith(OutputCaptureExtension.class)
class OpenAiCompatibleEmbeddingModelTest {
    @Test
    void shouldUseConfiguredEndpointCredentialsModelAndDimensionsInBoundedBatches() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        List<JsonNode> requests = new CopyOnWriteArrayList<>();
        List<String> authorizations = new CopyOnWriteArrayList<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/embeddings", exchange -> {
            JsonNode request = mapper.readTree(exchange.getRequestBody());
            requests.add(request);
            authorizations.add(exchange.getRequestHeaders().getFirst("Authorization"));
            List<Object> rows = new ArrayList<>();
            for (int i = 0; i < request.get("input").size(); i++) {
                rows.add(java.util.Map.of("index", i, "embedding", List.of(0.1, 0.2)));
            }
            byte[] body = mapper.writeValueAsBytes(java.util.Map.of("data", rows,
                    "usage", java.util.Map.of("prompt_tokens", 1, "total_tokens", 1)));
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        try {
            ChatConfig config = new ChatConfig();
            ReflectionTestUtils.setField(config, "embeddingProvider", "openai-compatible");
            ReflectionTestUtils.setField(config, "embeddingBaseUrl", "http://127.0.0.1:" + server.getAddress().getPort() + "/v1");
            ReflectionTestUtils.setField(config, "embeddingApiKey", "unit-test-only-key");
            ReflectionTestUtils.setField(config, "embeddingModelName", "text-embedding-v4");
            ReflectionTestUtils.setField(config, "embeddingDimensions", 2);
            EmbeddingModel model = config.embeddingModel();

            assertThat(model.embedAll(IntStream.range(0, 11)
                    .mapToObj(i -> TextSegment.from("测试文本" + i)).toList()).content()).hasSize(11);

            assertThat(requests).hasSize(2);
            assertThat(requests.get(0).get("input").size()).isEqualTo(10);
            assertThat(requests.get(1).get("input").get(0).asText()).isEqualTo("测试文本10");
            assertThat(requests).allSatisfy(request -> {
                assertThat(request.get("model").asText()).isEqualTo("text-embedding-v4");
                assertThat(request.get("dimensions").asInt()).isEqualTo(2);
            });
            assertThat(authorizations).containsOnly("Bearer unit-test-only-key");
        } finally {
            server.stop(0);
        }
    }

    @ParameterizedTest
    @ValueSource(ints = {401, 429})
    void shouldNotRetryOrLogHttpFailure(int status, CapturedOutput output) throws Exception {
        AtomicInteger requests = new AtomicInteger();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/embeddings", exchange -> {
            exchange.getRequestBody().readAllBytes();
            boolean firstRequest = requests.incrementAndGet() == 1;
            // A retry succeeds so regressions fail promptly rather than leaving an endless retry loop.
            String response = firstRequest
                    ? "{\"error\":{\"message\":\"sensitive-provider-body-fixture\",\"type\":\"api_error\"}}"
                    : "{\"data\":[{\"index\":0,\"embedding\":[0.1,0.2]}],\"usage\":{\"prompt_tokens\":1,\"total_tokens\":1}}";
            byte[] body = response.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(firstRequest ? status : 200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        try {
            ChatConfig config = new ChatConfig();
            ReflectionTestUtils.setField(config, "embeddingProvider", "openai-compatible");
            ReflectionTestUtils.setField(config, "embeddingBaseUrl", "http://127.0.0.1:" + server.getAddress().getPort() + "/v1");
            ReflectionTestUtils.setField(config, "embeddingApiKey", "unit-test-only-key");
            ReflectionTestUtils.setField(config, "embeddingDimensions", 2);
            EmbeddingModel model = config.embeddingModel();

            IllegalStateException failure = assertTimeoutPreemptively(Duration.ofSeconds(5),
                    () -> assertThrows(IllegalStateException.class, () -> model.embed("submitted-test-text")));

            assertThat(requests).hasValue(1);
            assertThat(failure).hasMessageNotContaining("sensitive-provider-body-fixture").hasNoCause();
            assertThat(output.getAll()).doesNotContain("sensitive-provider-body-fixture");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldRejectMissingOrWrongSizedVectorsBeforeIndexing() {
        EmbeddingModel missing = segments -> Response.from(List.of());
        EmbeddingModel wrongSize = segments -> Response.from(List.of(Embedding.from(new float[]{0.1f})));
        for (EmbeddingModel delegate : List.of(missing, wrongSize)) {
            EmbeddingModel model = new OpenAiCompatibleEmbeddingModel(delegate, 2, 10);
            assertThatThrownBy(() -> model.embed("test")).isInstanceOf(IllegalStateException.class);
        }
    }

    @Test
    void shouldNotExposeProviderErrorBodiesOrReturnPartialResults() {
        EmbeddingModel failing = segments -> { throw new IllegalStateException("secret-provider-response"); };
        EmbeddingModel model = new OpenAiCompatibleEmbeddingModel(failing, 2, 10);
        assertThatThrownBy(() -> model.embed("test"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageNotContaining("secret-provider-response")
                .hasNoCause();
        assertThat(model.embedAll(List.of()).content()).isEmpty();
    }
}
