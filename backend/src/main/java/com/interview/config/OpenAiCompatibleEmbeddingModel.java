package com.interview.config;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;

import java.util.ArrayList;
import java.util.List;

/** Applies the provider's batch limit while reusing the existing OpenAI SDK. */
public class OpenAiCompatibleEmbeddingModel implements EmbeddingModel {
    private final EmbeddingModel delegate;
    private final int dimensions;
    private final int batchSize;

    public OpenAiCompatibleEmbeddingModel(EmbeddingModel delegate, int dimensions, int batchSize) {
        if (dimensions < 1 || batchSize < 1) {
            throw new IllegalArgumentException("Embedding dimensions and batch size must be positive");
        }
        this.delegate = delegate;
        this.dimensions = dimensions;
        this.batchSize = batchSize;
    }

    @Override
    public Response<List<Embedding>> embedAll(List<TextSegment> segments) {
        List<Embedding> embeddings = new ArrayList<>();
        for (int offset = 0; offset < segments.size(); offset += batchSize) {
            List<TextSegment> batch = segments.subList(offset, Math.min(offset + batchSize, segments.size()));
            List<Embedding> result;
            try {
                result = delegate.embedAll(batch).content();
            } catch (RuntimeException e) {
                // Provider responses can contain submitted text or credentials; do not propagate them.
                throw new IllegalStateException("Embedding API request failed; check service credentials, quota and connectivity");
            }
            if (result == null || result.size() != batch.size()) {
                throw new IllegalStateException("Embedding API returned an unexpected vector count");
            }
            for (Embedding embedding : result) {
                if (embedding == null || embedding.vector().length != dimensions) {
                    throw new IllegalStateException("Embedding API returned an unexpected vector dimension");
                }
                for (float value : embedding.vector()) {
                    if (!Float.isFinite(value)) {
                        throw new IllegalStateException("Embedding API returned a non-finite vector value");
                    }
                }
            }
            embeddings.addAll(result);
        }
        return Response.from(embeddings);
    }
}
