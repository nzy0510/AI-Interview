package com.interview.service;

import com.interview.config.PositionCategoryConfig;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.content.retriever.EmbeddingStoreContentRetriever;
import dev.langchain4j.rag.query.Query;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.filter.Filter;

import java.util.ArrayList;
import java.util.List;

import static dev.langchain4j.store.embedding.filter.MetadataFilterBuilder.metadataKey;

/**
 * RAG 知识原子检索器：封装向量检索 + 岗位分类过滤 + 已用原子黑名单。
 */
public class RagRetriever {

    private final EmbeddingStore<TextSegment> embeddingStore;
    private final EmbeddingModel embeddingModel;
    private final PositionCategoryConfig categoryConfig;

    public RagRetriever(EmbeddingStore<TextSegment> embeddingStore, EmbeddingModel embeddingModel,
                        PositionCategoryConfig categoryConfig) {
        this.embeddingStore = embeddingStore;
        this.embeddingModel = embeddingModel;
        this.categoryConfig = categoryConfig;
    }

    /**
     * 根据岗位和用户消息检索 Top-3 相关知识原子，自动排除已用原子。
     */
    public List<Content> retrieve(String position, String userMessage, List<String> usedAtomIds) {
        List<String> categories = categoryConfig.getCategoriesFor(position);

        Filter categoryFilter = metadataKey("category").isEqualTo(categories.get(0));
        for (int i = 1; i < categories.size(); i++) {
            categoryFilter = categoryFilter.or(metadataKey("category").isEqualTo(categories.get(i)));
        }

        Filter finalFilter = usedAtomIds.isEmpty()
                ? categoryFilter
                : categoryFilter.and(metadataKey("id").isNotIn(usedAtomIds));

        ContentRetriever retriever = EmbeddingStoreContentRetriever.builder()
                .embeddingStore(embeddingStore)
                .embeddingModel(embeddingModel)
                .filter(finalFilter)
                .maxResults(3)
                .minScore(0.6)
                .build();

        if (userMessage != null && userMessage.trim().length() > 2) {
            return retriever.retrieve(Query.from(userMessage));
        }
        return new ArrayList<>();
    }
}
