package com.interview.config;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.output.Response;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

@DisplayName("HTTP embedding model")
class HttpEmbeddingModelTest {

    @Test
    @DisplayName("posts batched texts and parses embedding vectors")
    void shouldPostBatchedTextsAndParseEmbeddings() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        server.expect(requestTo("http://embedding-service:8000/embed"))
                .andExpect(content().json("{\"texts\":[\"query: 你好\",\"passage: 知识库\"]}"))
                .andRespond(withSuccess(
                        "{\"embeddings\":[[0.1,0.2],[0.3,0.4]]}",
                        MediaType.APPLICATION_JSON));
        HttpEmbeddingModel model = new HttpEmbeddingModel("http://embedding-service:8000/embed", restTemplate);

        Response<List<dev.langchain4j.data.embedding.Embedding>> response = model.embedAll(List.of(
                TextSegment.from("query: 你好"),
                TextSegment.from("passage: 知识库")));

        assertThat(response.content()).hasSize(2);
        assertThat(response.content().get(0).vectorAsList()).containsExactly(0.1f, 0.2f);
        assertThat(response.content().get(1).vectorAsList()).containsExactly(0.3f, 0.4f);
        server.verify();
    }
}
