package com.interview.service.questionbank.build;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class QuestionBankBuildCheckpoint {
    private final Set<Integer> completedChunkIndexes;
    private GenerationInvocation generation;
    private GenerationInvocation lastRejectedGeneration;

    private QuestionBankBuildCheckpoint(Set<Integer> completedChunkIndexes,
                                        GenerationInvocation generation,
                                        GenerationInvocation lastRejectedGeneration) {
        this.completedChunkIndexes = completedChunkIndexes;
        this.generation = generation;
        this.lastRejectedGeneration = lastRejectedGeneration;
    }

    static QuestionBankBuildCheckpoint parse(String json) {
        if (json == null || json.isBlank()) return new QuestionBankBuildCheckpoint(new HashSet<>(), null, null);
        try {
            if (json.trim().startsWith("[")) {
                return new QuestionBankBuildCheckpoint(new HashSet<>(JSON.parseArray(json, Integer.class)), null, null);
            }
            JSONObject state = JSON.parseObject(json);
            List<Integer> completed = state.getList("completedChunkIndexes", Integer.class);
            return new QuestionBankBuildCheckpoint(
                    new HashSet<>(completed == null ? List.of() : completed),
                    parseInvocation(state.getJSONObject("generation")),
                    parseInvocation(state.getJSONObject("lastRejectedGeneration")));
        } catch (Exception e) {
            throw new IllegalStateException("构建检查点损坏，为避免重复模型调用已停止任务");
        }
    }

    String toJson() {
        Map<String, Object> state = new LinkedHashMap<>();
        state.put("completedChunkIndexes", completedChunkIndexes.stream().sorted().toList());
        if (generation != null) {
            state.put("generation", invocationMap(generation));
        }
        if (lastRejectedGeneration != null) {
            state.put("lastRejectedGeneration", invocationMap(lastRejectedGeneration));
        }
        return JSON.toJSONString(state);
    }

    Set<Integer> completedChunkIndexes() { return completedChunkIndexes; }
    GenerationInvocation generation() { return generation; }
    String persistedResponse(int chunkIndex) {
        return generation != null && generation.chunkIndex() == chunkIndex ? generation.response() : null;
    }
    void startGeneration(int chunkIndex, int retryCount) {
        generation = new GenerationInvocation(chunkIndex, retryCount, null);
    }
    void persistGenerationResponse(String response) {
        generation = new GenerationInvocation(generation.chunkIndex(), generation.startedRetryCount(), response);
    }
    void retryRejectedGeneration(int chunkIndex, int retryCount) {
        if (generation == null || generation.chunkIndex() != chunkIndex || generation.response() == null) {
            throw new IllegalStateException("无效模型响应检查点与当前分块不一致");
        }
        lastRejectedGeneration = generation;
        generation = new GenerationInvocation(chunkIndex, retryCount, null);
    }
    void clearGeneration() { generation = null; }

    private static GenerationInvocation parseInvocation(JSONObject value) {
        return value == null ? null : new GenerationInvocation(
                value.getIntValue("chunkIndex"),
                value.getIntValue("startedRetryCount"),
                value.getString("response"));
    }

    private static Map<String, Object> invocationMap(GenerationInvocation invocation) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("chunkIndex", invocation.chunkIndex());
        value.put("startedRetryCount", invocation.startedRetryCount());
        value.put("response", invocation.response());
        return value;
    }

    record GenerationInvocation(int chunkIndex, int startedRetryCount, String response) {
    }
}
