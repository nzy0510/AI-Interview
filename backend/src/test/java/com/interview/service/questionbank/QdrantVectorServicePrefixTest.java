package com.interview.service.questionbank;

import com.interview.entity.KnowledgeAtom;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

@DisplayName("Qdrant vector embedding prefixes")
class QdrantVectorServicePrefixTest {

    @Test
    @DisplayName("adds query prefix when embedding search text")
    void shouldAddQueryPrefixWhenSearching() {
        CapturingEmbeddingModel embeddingModel = new CapturingEmbeddingModel();
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        QdrantVectorService service = new QdrantVectorService(embeddingModel, restTemplate);
        configure(service);
        server.expect(requestTo("http://qdrant/collections/test_atoms"))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));
        server.expect(requestTo("http://qdrant/collections/test_atoms/points/search"))
                .andRespond(withSuccess("{\"result\":[]}", MediaType.APPLICATION_JSON));

        service.search("候选人提到了 RAG", List.of("AI大模型"), List.of(), 20);

        assertThat(embeddingModel.texts).containsExactly("query: 候选人提到了 RAG");
        server.verify();
    }

    @Test
    @DisplayName("adds passage prefix when embedding atom text")
    void shouldAddPassagePrefixWhenUpserting() {
        CapturingEmbeddingModel embeddingModel = new CapturingEmbeddingModel();
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        QdrantVectorService service = new QdrantVectorService(embeddingModel, restTemplate);
        configure(service);
        server.expect(requestTo("http://qdrant/collections/test_atoms"))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));
        server.expect(requestTo("http://qdrant/collections/test_atoms/points?wait=true"))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        service.upsert(publishedAtom());

        assertThat(embeddingModel.texts).hasSize(1);
        assertThat(embeddingModel.texts.get(0)).startsWith("passage: 考核点: RAG流程");
        server.verify();
    }

    @Test
    @DisplayName("rejects an existing collection with a different vector size")
    void shouldRejectCollectionWithDifferentVectorSize() {
        CapturingEmbeddingModel embeddingModel = new CapturingEmbeddingModel();
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        QdrantVectorService service = new QdrantVectorService(embeddingModel, restTemplate);
        configure(service);
        server.expect(requestTo("http://qdrant/collections/test_atoms"))
                .andRespond(withSuccess("""
                        {
                          "result": {
                            "config": {
                              "params": {
                                "vectors": {
                                  "size": 384,
                                  "distance": "Cosine"
                                }
                              }
                            }
                          }
                        }
                        """, MediaType.APPLICATION_JSON));

        assertThatThrownBy(service::ensureCollection)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("expected 768")
                .hasMessageContaining("actual 384");
        server.verify();
    }

    @Test
    @DisplayName("returns a retryable failure when upsert finds a vector size mismatch")
    void shouldReturnFalseWhenUpsertFindsDifferentVectorSize() {
        CapturingEmbeddingModel embeddingModel = new CapturingEmbeddingModel();
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        QdrantVectorService service = new QdrantVectorService(embeddingModel, restTemplate);
        configure(service);
        server.expect(requestTo("http://qdrant/collections/test_atoms"))
                .andRespond(withSuccess("""
                        {
                          "result": {
                            "config": {
                              "params": {
                                "vectors": {
                                  "size": 384,
                                  "distance": "Cosine"
                                }
                              }
                            }
                          }
                        }
                        """, MediaType.APPLICATION_JSON));

        assertThat(service.upsert(publishedAtom())).isFalse();
        assertThat(embeddingModel.texts).isEmpty();
        server.verify();
    }

    @Test
    @DisplayName("default Qdrant client has bounded connect and read timeouts")
    void shouldConfigureQdrantHttpTimeouts() {
        QdrantVectorService service = new QdrantVectorService(new CapturingEmbeddingModel());

        RestTemplate restTemplate = (RestTemplate) ReflectionTestUtils.getField(service, "restTemplate");
        assertThat(restTemplate).isNotNull();
        assertThat(restTemplate.getRequestFactory()).isInstanceOf(SimpleClientHttpRequestFactory.class);
        SimpleClientHttpRequestFactory factory =
                (SimpleClientHttpRequestFactory) restTemplate.getRequestFactory();
        assertThat((int) ReflectionTestUtils.getField(factory, "connectTimeout")).isPositive();
        assertThat((int) ReflectionTestUtils.getField(factory, "readTimeout")).isPositive();
    }

    private void configure(QdrantVectorService service) {
        ReflectionTestUtils.setField(service, "enabled", true);
        ReflectionTestUtils.setField(service, "qdrantUrl", "http://qdrant");
        ReflectionTestUtils.setField(service, "collectionName", "test_atoms");
        ReflectionTestUtils.setField(service, "vectorSize", 768);
        ReflectionTestUtils.setField(service, "queryPrefix", "query:");
        ReflectionTestUtils.setField(service, "passagePrefix", "passage:");
    }

    private KnowledgeAtom publishedAtom() {
        KnowledgeAtom atom = new KnowledgeAtom();
        atom.setAtomId("rag-full-process");
        atom.setSubject("RAG流程");
        atom.setCategory("AI大模型");
        atom.setDifficulty("mid");
        atom.setPrinciples("检索增强生成流程");
        atom.setStatus("PUBLISHED");
        return atom;
    }

    private static class CapturingEmbeddingModel implements EmbeddingModel {
        private final List<String> texts = new ArrayList<>();

        @Override
        public Response<List<Embedding>> embedAll(List<TextSegment> textSegments) {
            List<Embedding> embeddings = new ArrayList<>();
            for (TextSegment segment : textSegments) {
                texts.add(segment.text());
                embeddings.add(Embedding.from(new float[]{0.1f, 0.2f}));
            }
            return Response.from(embeddings);
        }
    }
}
