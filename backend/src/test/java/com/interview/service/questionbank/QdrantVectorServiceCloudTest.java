package com.interview.service.questionbank;

import com.interview.entity.KnowledgeAtom;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class QdrantVectorServiceCloudTest {
    private final RestTemplate client = new RestTemplate();
    private final MockRestServiceServer server = MockRestServiceServer.bindTo(client).build();
    private final EmbeddingModel embedding = segments -> Response.from(List.of(Embedding.from(new float[]{0.1f, 0.2f})));
    private final QdrantVectorService service = configuredService();
    private static final String COLLECTION = "https://qdrant.example/collections/cloud_atoms";

    @Test
    void shouldAuthenticateQueriesDeletesAndAvailabilityChecks() {
        server.expect(requestTo(COLLECTION)).andExpect(header("api-key", "unit-test-qdrant-key"))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));
        server.expect(requestTo(COLLECTION + "/points/search"))
                .andExpect(header("api-key", "unit-test-qdrant-key"))
                .andExpect(jsonPath("$.filter.must[3].match.value").value("PUBLIC"))
                .andExpect(jsonPath("$.filter.must[5].match.value").value(42))
                .andRespond(withSuccess("{\"result\":[]}", MediaType.APPLICATION_JSON));
        server.expect(requestTo(COLLECTION + "/points/delete?wait=true"))
                .andExpect(header("api-key", "unit-test-qdrant-key"))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));
        server.expect(requestTo(COLLECTION)).andExpect(header("api-key", "unit-test-qdrant-key"))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED));

        assertThat(service.search("question", List.of(), List.of(), 2, "PUBLIC", null, 42L, 8L)).isEmpty();
        assertThat(service.delete("atom")).isTrue();
        assertThat(service.isAvailable()).isFalse();
        server.verify();
    }

    @Test
    void shouldNotCreateACollectionWhenAuthenticationFails() {
        server.expect(requestTo(COLLECTION)).andRespond(withStatus(HttpStatus.FORBIDDEN));
        assertThat(service.ensureCollection()).isFalse();
        server.verify();
    }

    @Test
    void shouldNotWriteWhenASearchFindsAMissingCollection() {
        server.expect(requestTo(COLLECTION)).andRespond(withStatus(HttpStatus.NOT_FOUND));
        assertThatThrownBy(() -> service.search("test", List.of(), List.of(), 1))
                .isInstanceOf(IllegalStateException.class);
        server.verify();
    }

    @Test
    void shouldInitializeFilterIndexesBeforeAuthenticatedUpsert() {
        ReflectionTestUtils.setField(service, "initializePayloadIndexes", true);
        server.expect(requestTo(COLLECTION)).andRespond(withStatus(HttpStatus.NOT_FOUND));
        server.expect(requestTo(COLLECTION)).andExpect(method(HttpMethod.PUT))
                .andExpect(header("api-key", "unit-test-qdrant-key"))
                .andExpect(jsonPath("$.vectors.size").value(2))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));
        for (String field : List.of("atom_id", "category", "status", "publication_status", "vector_status", "scope",
                "owner_user_id", "position_id", "knowledge_base_id")) {
            server.expect(requestTo(COLLECTION + "/index?wait=true"))
                    .andExpect(header("api-key", "unit-test-qdrant-key"))
                    .andExpect(jsonPath("$.field_name").value(field))
                    .andExpect(jsonPath("$.field_schema").value(field.endsWith("_id") && !field.equals("atom_id") ? "integer" : "keyword"))
                    .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));
        }
        server.expect(requestTo(COLLECTION + "/points?wait=true"))
                .andExpect(header("api-key", "unit-test-qdrant-key"))
                .andExpect(jsonPath("$.points[0].payload.scope").value("PUBLIC"))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));
        KnowledgeAtom atom = new KnowledgeAtom();
        atom.setAtomId("test");
        atom.setStatus("PUBLISHED");
        atom.setScope("PUBLIC");
        assertThat(service.upsert(atom)).isTrue();
        server.verify();
    }

    private QdrantVectorService configuredService() {
        QdrantVectorService instance = new QdrantVectorService(embedding, client);
        ReflectionTestUtils.setField(instance, "enabled", true);
        ReflectionTestUtils.setField(instance, "qdrantUrl", "https://qdrant.example");
        ReflectionTestUtils.setField(instance, "collectionName", "cloud_atoms");
        ReflectionTestUtils.setField(instance, "vectorSize", 2);
        ReflectionTestUtils.setField(instance, "apiKey", "unit-test-qdrant-key");
        return instance;
    }
}
