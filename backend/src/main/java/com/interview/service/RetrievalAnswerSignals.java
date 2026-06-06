package com.interview.service;

import java.util.List;

final class RetrievalAnswerSignals {

    private static final List<String> LOW_INFORMATION_PHRASES = List.of(
            "不会", "不知道", "不清楚", "不了解", "没做过", "没有做过", "不太会",
            "不太清楚", "不熟", "不是很了解", "忘了", "说不上来", "没接触",
            "没用过", "不懂");
    private static final List<String> TECHNICAL_TERMS = List.of(
            "rag", "lora", "agent", "mcp", "a2a", "llm", "prompt", "embedding", "向量",
            "检索", "召回", "微调", "注意力", "transformer", "token", "上下文", "幻觉",
            "推理", "工具调用", "function calling", "langchain", "langgraph", "qdrant",
            "rerank", "重排", "多模态", "量化", "蒸馏", "对齐", "rlhf", "sft", "低秩");

    boolean isLowInformationAnswer(String message) {
        if (message == null) return true;
        String normalized = normalize(message);
        for (String phrase : LOW_INFORMATION_PHRASES) {
            if (normalized.contains(phrase)) return true;
        }
        return normalized.length() <= 4 && technicalSignalCount(normalized) == 0;
    }

    int effectiveRetrievalLimit(String message, int retrievalLimit, int maxRetrievalLimit) {
        int baseLimit = normalizeLimit(retrievalLimit, maxRetrievalLimit);
        int maxLimit = normalizeMaxRetrievalLimit(maxRetrievalLimit);
        String normalized = normalize(message);
        if (isLowInformationAnswer(normalized) || maxLimit <= baseLimit) {
            return baseLimit;
        }
        int technicalSignals = technicalSignalCount(normalized);
        if ((normalized.length() <= 20 && technicalSignals >= 1) || technicalSignals >= 3) {
            return maxLimit;
        }
        return baseLimit;
    }

    int normalizeLimit(int limit, int maxRetrievalLimit) {
        return Math.max(1, Math.min(limit, normalizeMaxRetrievalLimit(maxRetrievalLimit)));
    }

    int normalizeMaxRetrievalLimit(int maxRetrievalLimit) {
        return Math.max(1, maxRetrievalLimit);
    }

    private int technicalSignalCount(String normalizedText) {
        int count = 0;
        for (String term : TECHNICAL_TERMS) {
            if (normalizedText.contains(term.toLowerCase())) count++;
        }
        return count;
    }

    private String normalize(String value) {
        return value == null ? "" : value.replaceAll("\\s+", "").toLowerCase();
    }
}
