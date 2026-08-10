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
    private PlanningInvocation categoryPlanning;
    private PlanningInvocation lastRejectedCategoryPlanning;
    private GenerationInvocation generation;
    private GenerationInvocation lastRejectedGeneration;
    private SupervisionInvocation supervision;
    private RepairInvocation repair;

    private QuestionBankBuildCheckpoint(Set<Integer> completedChunkIndexes,
                                         PlanningInvocation categoryPlanning,
                                         PlanningInvocation lastRejectedCategoryPlanning,
                                         GenerationInvocation generation,
                                         GenerationInvocation lastRejectedGeneration,
                                         SupervisionInvocation supervision,
                                         RepairInvocation repair) {
        this.completedChunkIndexes = completedChunkIndexes;
        this.categoryPlanning = categoryPlanning;
        this.lastRejectedCategoryPlanning = lastRejectedCategoryPlanning;
        this.generation = generation;
        this.lastRejectedGeneration = lastRejectedGeneration;
        this.supervision = supervision;
        this.repair = repair;
    }

    static QuestionBankBuildCheckpoint parse(String json) {
        if (json == null || json.isBlank()) return new QuestionBankBuildCheckpoint(new HashSet<>(), null, null, null, null, null, null);
        try {
            if (json.trim().startsWith("[")) {
                return new QuestionBankBuildCheckpoint(new HashSet<>(JSON.parseArray(json, Integer.class)), null, null, null, null, null, null);
            }
            JSONObject state = JSON.parseObject(json);
            List<Integer> completed = state.getList("completedChunkIndexes", Integer.class);
            return new QuestionBankBuildCheckpoint(
                    new HashSet<>(completed == null ? List.of() : completed),
                    parsePlanningInvocation(state.getJSONObject("categoryPlanning")),
                    parsePlanningInvocation(state.getJSONObject("lastRejectedCategoryPlanning")),
                    parseInvocation(state.getJSONObject("generation")),
                    parseInvocation(state.getJSONObject("lastRejectedGeneration")),
                    parseSupervisionInvocation(state.getJSONObject("supervision")),
                    parseRepairInvocation(state.getJSONObject("repair")));
        } catch (Exception e) {
            throw new IllegalStateException("构建检查点损坏，为避免重复模型调用已停止任务");
        }
    }

    String toJson() {
        Map<String, Object> state = new LinkedHashMap<>();
        state.put("completedChunkIndexes", completedChunkIndexes.stream().sorted().toList());
        if (categoryPlanning != null) {
            state.put("categoryPlanning", planningInvocationMap(categoryPlanning));
        }
        if (lastRejectedCategoryPlanning != null) {
            state.put("lastRejectedCategoryPlanning", planningInvocationMap(lastRejectedCategoryPlanning));
        }
        if (generation != null) {
            state.put("generation", invocationMap(generation));
        }
        if (lastRejectedGeneration != null) {
            state.put("lastRejectedGeneration", invocationMap(lastRejectedGeneration));
        }
        if (supervision != null) {
            state.put("supervision", supervisionInvocationMap(supervision));
        }
        if (repair != null) {
            state.put("repair", repairInvocationMap(repair));
        }
        return JSON.toJSONString(state);
    }

    Set<Integer> completedChunkIndexes() { return completedChunkIndexes; }
    PlanningInvocation categoryPlanning() { return categoryPlanning; }
    String persistedCategoryPlanningResponse() {
        return categoryPlanning == null ? null : categoryPlanning.response();
    }
    void startCategoryPlanning(int retryCount) {
        categoryPlanning = new PlanningInvocation(retryCount, null);
    }
    void persistCategoryPlanningResponse(String response) {
        if (categoryPlanning == null) throw new IllegalStateException("分类规划调用检查点不存在");
        categoryPlanning = new PlanningInvocation(categoryPlanning.startedRetryCount(), response);
    }
    void retryRejectedCategoryPlanning(int retryCount) {
        if (categoryPlanning == null || categoryPlanning.response() == null) {
            throw new IllegalStateException("无效分类规划响应检查点不存在");
        }
        lastRejectedCategoryPlanning = categoryPlanning;
        categoryPlanning = new PlanningInvocation(retryCount, null);
    }
    void clearCategoryPlanning() { categoryPlanning = null; }
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
    SupervisionInvocation supervision() { return supervision; }
    void startSupervision(long candidateId, int retryCount) {
        supervision = new SupervisionInvocation(candidateId, retryCount, null);
    }
    void persistSupervisionResponse(String response) {
        if (supervision == null) throw new IllegalStateException("监督调用检查点不存在");
        supervision = new SupervisionInvocation(
                supervision.candidateId(), supervision.startedRetryCount(), response);
    }
    void clearSupervision() { supervision = null; }
    RepairInvocation repair() { return repair; }
    void startRepair(long candidateId, int round, int retryCount) {
        repair = new RepairInvocation(candidateId, round, retryCount, null);
    }
    void persistRepairResponse(String response) {
        if (repair == null) throw new IllegalStateException("修复调用检查点不存在");
        repair = new RepairInvocation(repair.candidateId(), repair.round(), repair.startedRetryCount(), response);
    }
    void clearRepair() { repair = null; }

    private static GenerationInvocation parseInvocation(JSONObject value) {
        return value == null ? null : new GenerationInvocation(
                value.getIntValue("chunkIndex"),
                value.getIntValue("startedRetryCount"),
                value.getString("response"));
    }

    private static PlanningInvocation parsePlanningInvocation(JSONObject value) {
        return value == null ? null : new PlanningInvocation(
                value.getIntValue("startedRetryCount"),
                value.getString("response"));
    }

    private static RepairInvocation parseRepairInvocation(JSONObject value) {
        return value == null ? null : new RepairInvocation(
                value.getLongValue("candidateId"),
                value.getIntValue("round"),
                value.getIntValue("startedRetryCount"),
                value.getString("response"));
    }

    private static SupervisionInvocation parseSupervisionInvocation(JSONObject value) {
        return value == null ? null : new SupervisionInvocation(
                value.getLongValue("candidateId"),
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

    private static Map<String, Object> planningInvocationMap(PlanningInvocation invocation) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("startedRetryCount", invocation.startedRetryCount());
        value.put("response", invocation.response());
        return value;
    }

    private static Map<String, Object> repairInvocationMap(RepairInvocation invocation) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("candidateId", invocation.candidateId());
        value.put("round", invocation.round());
        value.put("startedRetryCount", invocation.startedRetryCount());
        value.put("response", invocation.response());
        return value;
    }

    private static Map<String, Object> supervisionInvocationMap(SupervisionInvocation invocation) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("candidateId", invocation.candidateId());
        value.put("startedRetryCount", invocation.startedRetryCount());
        value.put("response", invocation.response());
        return value;
    }

    record GenerationInvocation(int chunkIndex, int startedRetryCount, String response) {
    }

    record PlanningInvocation(int startedRetryCount, String response) {
    }

    record SupervisionInvocation(long candidateId, int startedRetryCount, String response) {
    }

    record RepairInvocation(long candidateId, int round, int startedRetryCount, String response) {
    }
}

final class QuestionBankBuildLeaseLostException extends IllegalStateException {
    QuestionBankBuildLeaseLostException() {
        super("题库构建作业租约已失效，已停止继续处理");
    }

    QuestionBankBuildLeaseLostException(Throwable cause) {
        super("题库构建作业租约已失效，已停止继续处理", cause);
    }
}
