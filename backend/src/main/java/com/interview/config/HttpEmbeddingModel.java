package com.interview.config;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class HttpEmbeddingModel implements EmbeddingModel {

    private final String endpoint;
    private final RestTemplate restTemplate;

    public HttpEmbeddingModel(String endpoint, RestTemplate restTemplate) {
        this.endpoint = endpoint;
        this.restTemplate = restTemplate;
    }

    @Override
    public Response<List<Embedding>> embedAll(List<TextSegment> textSegments) {
        List<String> texts = textSegments.stream().map(TextSegment::text).toList();
        Map<?, ?> response = restTemplate.postForObject(endpoint, Map.of("texts", texts), Map.class);
        Object rawEmbeddings = response != null ? response.get("embeddings") : null;
        if (!(rawEmbeddings instanceof List<?> rows)) {
            throw new IllegalStateException("Embedding service response missing embeddings");
        }
        if (rows.size() != texts.size()) {
            throw new IllegalStateException("Embedding service response count does not match request");
        }
        List<Embedding> embeddings = new ArrayList<>();
        for (Object row : rows) {
            if (!(row instanceof List<?> values)) {
                throw new IllegalStateException("Embedding service returned a non-vector row");
            }
            float[] vector = new float[values.size()];
            for (int i = 0; i < values.size(); i++) {
                Object value = values.get(i);
                if (!(value instanceof Number number)) {
                    throw new IllegalStateException("Embedding service vector contains a non-number value");
                }
                vector[i] = number.floatValue();
            }
            embeddings.add(Embedding.from(vector));
        }
        return Response.from(embeddings);
    }
}
