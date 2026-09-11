package com.interview.service.questionbank.build;

import com.interview.entity.AppJob;
import com.interview.entity.QuestionBankBuild;
import com.interview.mapper.QuestionBankBuildMapper;
import com.interview.service.AppJobService;

import java.time.Duration;
import java.util.function.BiConsumer;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import java.util.function.Supplier;

/** Owns recovery and retry authorization for the four document-build model calls. */
final class QuestionBankBuildModelCall {
    private static final Duration JOB_LEASE_TTL = Duration.ofMinutes(15);

    private final QuestionBankBuild build;
    private final AppJob job;
    private final QuestionBankBuildCheckpoint checkpoint;
    private final QuestionBankBuildMapper buildMapper;
    private final AppJobService appJobService;

    QuestionBankBuildModelCall(QuestionBankBuild build, AppJob job, QuestionBankBuildCheckpoint checkpoint,
                              QuestionBankBuildMapper buildMapper, AppJobService appJobService) {
        this.build = build;
        this.job = job;
        this.checkpoint = checkpoint;
        this.buildMapper = buildMapper;
        this.appJobService = appJobService;
    }

    /**
     * parse must only decode/validate response content. applyAndCommit receives a completed
     * checkpoint copy and must persist it after the domain result (or in the same write).
     * freshPrepare is only the pending-human-review CAS immediately before a new call.
     */
    <T> void execute(Key key, BooleanSupplier freshPrepare, Supplier<String> invoke,
                     Function<String, T> parse, BiConsumer<T, QuestionBankBuildCheckpoint> applyAndCommit) {
        QuestionBankBuildCheckpoint.Invocation saved = invocation(key);
        requireLease();
        if (saved != null && saved.response() != null) {
            T result;
            try {
                result = parse.apply(saved.response());
            } catch (RuntimeException invalidResponse) {
                if (retryCount() <= saved.startedRetryCount()) throw invalidResponse;
                invokeAndApply(key, true, freshPrepare, invoke, parse, applyAndCommit);
                return;
            }
            finish(key, result, applyAndCommit);
            return;
        }
        if (saved != null && retryCount() <= saved.startedRetryCount()) {
            throw new IllegalStateException(key.unconfirmedMessage());
        }
        invokeAndApply(key, false, freshPrepare, invoke, parse, applyAndCommit);
    }

    private <T> void invokeAndApply(Key key, boolean rejectedResponse, BooleanSupplier freshPrepare,
                                    Supplier<String> invoke, Function<String, T> parse,
                                    BiConsumer<T, QuestionBankBuildCheckpoint> applyAndCommit) {
        QuestionBankBuildCheckpoint started = checkpoint.copy();
        switch (key.kind()) {
            case CATEGORY_PLANNING -> {
                if (rejectedResponse) started.retryRejectedCategoryPlanning(retryCount());
                else started.startCategoryPlanning(retryCount());
            }
            case GENERATION -> {
                if (rejectedResponse) started.retryRejectedGeneration((int) key.targetId(), retryCount());
                else started.startGeneration((int) key.targetId(), retryCount());
            }
            case SUPERVISION -> started.startSupervision(key.targetId(), retryCount());
            case REPAIR -> started.startRepair(key.targetId(), key.round(), retryCount());
        }
        persist(started);
        requireLease();
        if (!freshPrepare.getAsBoolean()) {
            finish(key);
            return;
        }
        requireLease();
        String raw = invoke.get();
        requireLease();
        QuestionBankBuildCheckpoint responded = checkpoint.copy();
        switch (key.kind()) {
            case CATEGORY_PLANNING -> responded.persistCategoryPlanningResponse(raw);
            case GENERATION -> responded.persistGenerationResponse(raw);
            case SUPERVISION -> responded.persistSupervisionResponse(raw);
            case REPAIR -> responded.persistRepairResponse(raw);
        }
        persist(responded);
        requireLease();
        // A newly called response cannot consume the same retry authorization again.
        T result = parse.apply(raw);
        finish(key, result, applyAndCommit);
    }

    private <T> void finish(Key key, T result,
                            BiConsumer<T, QuestionBankBuildCheckpoint> applyAndCommit) {
        QuestionBankBuildCheckpoint finished = completed(key);
        requireLease();
        applyAndCommit.accept(result, finished);
        checkpoint.replaceWith(finished);
    }

    /** Used only after the domain has independently confirmed that its result already exists. */
    void finish(Key key) {
        invocation(key);
        persist(completed(key));
    }

    void persist(QuestionBankBuildCheckpoint next) {
        requireLease();
        String json = next.toJson();
        QuestionBankBuild update = new QuestionBankBuild();
        update.setId(build.getId());
        update.setCheckpointJson(json);
        update.setCompletedChunkCount(next.completedChunkIndexes().size());
        if (buildMapper.updateById(update) != 1) {
            throw new IllegalStateException("构建调用检查点保存失败，已停止继续处理");
        }
        build.setCheckpointJson(json);
        build.setCompletedChunkCount(next.completedChunkIndexes().size());
        checkpoint.replaceWith(next);
    }

    private QuestionBankBuildCheckpoint completed(Key key) {
        QuestionBankBuildCheckpoint finished = checkpoint.copy();
        switch (key.kind()) {
            case CATEGORY_PLANNING -> finished.clearCategoryPlanning();
            case GENERATION -> {
                finished.completedChunkIndexes().add((int) key.targetId());
                finished.clearGeneration();
            }
            case SUPERVISION -> finished.clearSupervision();
            case REPAIR -> finished.clearRepair();
        }
        return finished;
    }

    private QuestionBankBuildCheckpoint.Invocation invocation(Key key) {
        return switch (key.kind()) {
            case CATEGORY_PLANNING -> checkpoint.categoryPlanning();
            case GENERATION -> {
                var value = checkpoint.generation();
                if (value != null && value.chunkIndex() != key.targetId()) {
                    throw new IllegalStateException("模型调用检查点与当前分块不一致");
                }
                yield value;
            }
            case SUPERVISION -> {
                var value = checkpoint.supervision();
                if (value != null && value.candidateId() != key.targetId()) {
                    throw new IllegalStateException("监督调用检查点与当前候选不一致");
                }
                yield value;
            }
            case REPAIR -> {
                var value = checkpoint.repair();
                if (value != null && (value.candidateId() != key.targetId() || value.round() != key.round())) {
                    throw new IllegalStateException("修复调用检查点与当前候选不一致");
                }
                yield value;
            }
        };
    }

    private int retryCount() {
        return job.getRetryCount() == null ? 0 : job.getRetryCount();
    }

    private void requireLease() {
        if (job == null || job.getId() == null || job.getClaimedBy() == null
                || !appJobService.extendRunningJobLease(job.getId(), job.getClaimedBy(), JOB_LEASE_TTL)) {
            throw new QuestionBankBuildLeaseLostException();
        }
    }

    private enum Kind { CATEGORY_PLANNING, GENERATION, SUPERVISION, REPAIR }

    record Key(Kind kind, long targetId, int round) {
        static Key planning() { return new Key(Kind.CATEGORY_PLANNING, 0, 0); }
        static Key generation(int chunkIndex) { return new Key(Kind.GENERATION, chunkIndex, 0); }
        static Key supervision(long candidateId) { return new Key(Kind.SUPERVISION, candidateId, 0); }
        static Key repair(long candidateId, int round) { return new Key(Kind.REPAIR, candidateId, round); }

        String unconfirmedMessage() {
            String stage = switch (kind) {
                case CATEGORY_PLANNING -> "分类规划";
                case GENERATION -> "生成";
                case SUPERVISION -> "质量监督";
                case REPAIR -> "修复";
            };
            return "上次" + stage + "调用结果未确认，为避免重复计费未自动重调；请点击重试明确授权再次调用";
        }
    }
}
