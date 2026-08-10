package com.interview.service.questionbank.build;

import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.interview.config.QuestionBankBuildProperties;
import com.interview.entity.AppJob;
import com.interview.entity.QuestionBankBuild;
import com.interview.entity.QuestionBankBuildCandidate;
import com.interview.mapper.QuestionBankBuildCandidateMapper;
import com.interview.mapper.QuestionBankBuildMapper;
import com.interview.service.AppJobService;
import com.interview.service.UserLlmRuntimeConfig;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Runs the complete quality stage behind one interface: deterministic validation,
 * machine supervision, bounded repair and re-supervision.
 */
final class QuestionBankBuildSupervisionRunner {
    private static final String SUPERVISION_PROMPT_VERSION = "qb-supervision-v1";
    private static final Duration JOB_LEASE_TTL = Duration.ofMinutes(15);

    private final QuestionBankBuildMapper buildMapper;
    private final QuestionBankBuildCandidateMapper candidateMapper;
    private final AppJobService appJobService;
    private final QuestionBankBuildSupervisionService supervisionService;
    private final QuestionBankBuildRepairService repairService;
    private final QuestionBankBuildCandidateValidator validator;
    private final QuestionBankBuildProperties properties;

    QuestionBankBuildSupervisionRunner(QuestionBankBuildMapper buildMapper,
                                       QuestionBankBuildCandidateMapper candidateMapper,
                                       AppJobService appJobService,
                                       QuestionBankBuildSupervisionService supervisionService,
                                       QuestionBankBuildRepairService repairService,
                                       QuestionBankBuildCandidateValidator validator,
                                       QuestionBankBuildProperties properties) {
        this.buildMapper = buildMapper;
        this.candidateMapper = candidateMapper;
        this.appJobService = appJobService;
        this.supervisionService = supervisionService;
        this.repairService = repairService;
        this.validator = validator;
        this.properties = properties;
    }

    void run(QuestionBankBuild build,
             Map<Integer, String> sourceTextByChunk,
             UserLlmRuntimeConfig runtime,
             AppJob job,
             QuestionBankBuildCheckpoint checkpoint) {
        run(build, sourceTextByChunk, runtime, job, checkpoint, null);
    }

    void run(QuestionBankBuild build,
             Map<Integer, String> sourceTextByChunk,
             UserLlmRuntimeConfig runtime,
             AppJob job,
             QuestionBankBuildCheckpoint checkpoint,
             Set<Long> candidateScope) {
        requireCandidateScope(build.getId(), candidateScope);
        resumeSupervisionIfNeeded(build, sourceTextByChunk, runtime, job, checkpoint, candidateScope);
        resumeRepairIfNeeded(build, sourceTextByChunk, runtime, job, checkpoint, candidateScope);
        supervisePending(build, sourceTextByChunk, runtime, job, checkpoint,
                candidateScope, 65, 76, "SUPERVISING");
        updateRepairVerification(build, candidateScope, false, job);

        int maxRounds = Math.max(0, properties.getMaxRepairRounds());
        int startRound = Math.max(1, build.getRepairRound() == null ? 0 : build.getRepairRound());
        for (int round = startRound; round <= maxRounds; round++) {
            List<QuestionBankBuildCandidate> repairable = loadCandidates(build.getId()).stream()
                    .filter(candidate -> inScope(candidate, candidateScope))
                    .filter(this::canRepair)
                    .toList();
            if (repairable.isEmpty()) break;
            build.setRepairRound(round);
            updateProgress(build, job, "REPAIRING", Math.min(88, 76 + (round - 1) * 6));
            int completed = 0;
            for (QuestionBankBuildCandidate candidate : repairable) {
                if (candidate.getRepairRound() != null && candidate.getRepairRound() >= round) continue;
                ensureBuildExecutable(build.getId());
                String sourceText = sourceTextByChunk.get(candidate.getChunkIndex());
                if (sourceText == null) {
                    markRepairFailure(candidate, round, "候选原子缺少来源分块");
                } else {
                    repairCandidate(build, candidate, sourceText, runtime, job, checkpoint, round);
                }
                completed++;
                int progress = Math.min(90, 76 + (int) Math.round(completed * 12.0 / Math.max(1, repairable.size())));
                updateProgress(build, job, "REPAIRING", progress);
            }
            supervisePending(build, sourceTextByChunk, runtime, job, checkpoint,
                    candidateScope, 90, 96, "RESUPERVISING");
            updateRepairVerification(build, candidateScope, round >= maxRounds, job);
        }
        updateRepairVerification(build, candidateScope, true, job);
        refreshBuildCounts(build, job);
    }

    private void supervisePending(QuestionBankBuild build,
                                  Map<Integer, String> sourceTextByChunk,
                                  UserLlmRuntimeConfig runtime,
                                  AppJob job,
                                  QuestionBankBuildCheckpoint checkpoint,
                                  Set<Long> candidateScope,
                                  int startProgress,
                                  int endProgress,
                                  String stage) {
        List<QuestionBankBuildCandidate> candidates = loadCandidates(build.getId());
        if (candidates.isEmpty()) return;
        updateProgress(build, job, stage, startProgress);
        int total = (int) candidates.stream().filter(candidate -> inScope(candidate, candidateScope)).count();
        int completed = (int) candidates.stream()
                .filter(candidate -> inScope(candidate, candidateScope))
                .filter(this::machineReviewCompleted)
                .count();
        for (QuestionBankBuildCandidate candidate : candidates) {
            if (!inScope(candidate, candidateScope)) continue;
            if (hasHumanDecision(candidate)) {
                if (!machineReviewCompleted(candidate)) completed++;
                markMachineSkipped(candidate);
                continue;
            }
            if (machineReviewCompleted(candidate)) continue;
            if ("RUNNING".equalsIgnoreCase(candidate.getMachineReviewStatus())
                    || "FAILED".equalsIgnoreCase(candidate.getMachineReviewStatus())) {
                throw new IllegalStateException("上次质量监督结果未确认，为避免重复计费已停止；请点击重试");
            }
            ensureBuildExecutable(build.getId());
            String sourceText = sourceTextByChunk.get(candidate.getChunkIndex());
            if (sourceText == null) {
                markMachineNeedsHuman(candidate, "候选原子缺少来源分块");
                completed++;
                continue;
            }
            try {
                validateCandidate(build, candidate);
            } catch (RuntimeException e) {
                markMachineNeedsHuman(candidate, sanitize(e.getMessage()));
                completed++;
                continue;
            }
            QuestionBankBuildSupervisionContext context = new QuestionBankBuildSupervisionContext(
                    build, candidate, sourceText, runtime, candidates);
            QuestionBankBuildSupervisionResult precheck = supervisionService.precheck(context);
            if (precheck != null) {
                applyMachineResult(candidate, precheck);
            } else {
                superviseCandidate(build, candidate, context, job, checkpoint);
            }
            refreshBuildCounts(build, job);
            completed++;
            updateProgress(build, job, stage, progress(startProgress, endProgress, completed, total));
        }
        refreshBuildCounts(build, job);
    }

    private void superviseCandidate(QuestionBankBuild build,
                                    QuestionBankBuildCandidate candidate,
                                    QuestionBankBuildSupervisionContext context,
                                    AppJob job,
                                    QuestionBankBuildCheckpoint checkpoint) {
        QuestionBankBuildCheckpoint.SupervisionInvocation invocation = checkpoint.supervision();
        if (invocation != null && invocation.candidateId() != candidate.getId()) {
            throw new IllegalStateException("监督调用检查点与当前候选不一致");
        }
        int retryCount = job.getRetryCount() == null ? 0 : job.getRetryCount();
        String raw = invocation == null ? null : invocation.response();
        if (raw != null) {
            try {
                applyMachineResult(candidate, parseSupervisionResponse(raw));
                checkpoint.clearSupervision();
                persistCheckpoint(build, checkpoint, job);
                return;
            } catch (RuntimeException invalidResponse) {
                if (retryCount <= invocation.startedRetryCount()) throw invalidResponse;
            }
        } else if (invocation != null && retryCount <= invocation.startedRetryCount()) {
            throw new IllegalStateException("上次质量监督结果未确认，为避免重复计费已停止；请点击重试");
        }

        checkpoint.startSupervision(candidate.getId(), retryCount);
        persistCheckpoint(build, checkpoint, job);
        if (!markMachineRunning(candidate)) {
            checkpoint.clearSupervision();
            persistCheckpoint(build, checkpoint, job);
            return;
        }
        requireJobLease(job);
        raw = supervisionService.complete(context);
        requireJobLease(job);
        checkpoint.persistSupervisionResponse(raw);
        persistCheckpoint(build, checkpoint, job);
        QuestionBankBuildSupervisionResult result = parseSupervisionResponse(raw);
        applyMachineResult(candidate, result);
        checkpoint.clearSupervision();
        persistCheckpoint(build, checkpoint, job);
    }

    private void resumeSupervisionIfNeeded(QuestionBankBuild build,
                                           Map<Integer, String> sourceTextByChunk,
                                           UserLlmRuntimeConfig runtime,
                                           AppJob job,
                                           QuestionBankBuildCheckpoint checkpoint,
                                           Set<Long> candidateScope) {
        QuestionBankBuildCheckpoint.SupervisionInvocation invocation = checkpoint.supervision();
        if (invocation == null) return;
        QuestionBankBuildCandidate candidate = candidateMapper.selectById(invocation.candidateId());
        if (candidate == null || !Objects.equals(candidate.getBuildId(), build.getId())
                || !inScope(candidate, candidateScope)) {
            throw new IllegalStateException("监督调用检查点对应的候选不存在或不在本次处理范围");
        }
        if (machineReviewCompleted(candidate)) {
            checkpoint.clearSupervision();
            persistCheckpoint(build, checkpoint, job);
            return;
        }
        String sourceText = sourceTextByChunk.get(candidate.getChunkIndex());
        if (sourceText == null) throw new IllegalStateException("监督调用检查点对应的来源分块不存在");
        QuestionBankBuildSupervisionContext context = new QuestionBankBuildSupervisionContext(
                build, candidate, sourceText, runtime, loadCandidates(build.getId()));
        superviseCandidate(build, candidate, context, job, checkpoint);
    }

    private void repairCandidate(QuestionBankBuild build,
                                 QuestionBankBuildCandidate candidate,
                                 String sourceText,
                                 UserLlmRuntimeConfig runtime,
                                 AppJob job,
                                 QuestionBankBuildCheckpoint checkpoint,
                                 int round) {
        QuestionBankBuildCheckpoint.RepairInvocation invocation = checkpoint.repair();
        if (invocation != null && (invocation.candidateId() != candidate.getId() || invocation.round() != round)) {
            throw new IllegalStateException("修复调用检查点与当前候选不一致");
        }
        int retryCount = job.getRetryCount() == null ? 0 : job.getRetryCount();
        QuestionBankBuildRepairContext context = new QuestionBankBuildRepairContext(
                build, candidate, sourceText, runtime, round);
        String raw = invocation == null ? null : invocation.response();
        if (raw != null) {
            try {
                applyRepairResult(build, candidate, parseRepairResponse(build, candidate, raw), round);
                checkpoint.clearRepair();
                persistCheckpoint(build, checkpoint, job);
                refreshBuildCounts(build, job);
                return;
            } catch (RuntimeException invalidResponse) {
                if (retryCount <= invocation.startedRetryCount()) throw invalidResponse;
            }
        } else if (invocation != null && retryCount <= invocation.startedRetryCount()) {
            throw new IllegalStateException("上次修复调用结果未确认，为避免重复计费未自动重调；请点击重试明确授权再次调用");
        }

        checkpoint.startRepair(candidate.getId(), round, retryCount);
        persistCheckpoint(build, checkpoint, job);
        if (!markRepairRunning(candidate, round)) {
            checkpoint.clearRepair();
            persistCheckpoint(build, checkpoint, job);
            return;
        }
        requireJobLease(job);
        raw = repairService.complete(context);
        requireJobLease(job);
        checkpoint.persistRepairResponse(raw);
        persistCheckpoint(build, checkpoint, job);
        applyRepairResult(build, candidate, parseRepairResponse(build, candidate, raw), round);
        checkpoint.clearRepair();
        persistCheckpoint(build, checkpoint, job);
        refreshBuildCounts(build, job);
    }

    private void resumeRepairIfNeeded(QuestionBankBuild build,
                                      Map<Integer, String> sourceTextByChunk,
                                      UserLlmRuntimeConfig runtime,
                                      AppJob job,
                                      QuestionBankBuildCheckpoint checkpoint,
                                      Set<Long> candidateScope) {
        QuestionBankBuildCheckpoint.RepairInvocation invocation = checkpoint.repair();
        if (invocation == null) return;
        QuestionBankBuildCandidate candidate = candidateMapper.selectById(invocation.candidateId());
        if (candidate == null || !Objects.equals(candidate.getBuildId(), build.getId())
                || !inScope(candidate, candidateScope)) {
            throw new IllegalStateException("修复调用检查点对应的候选不存在");
        }
        if (hasHumanDecision(candidate) || repairCheckpointAlreadyApplied(candidate, invocation.round())) {
            checkpoint.clearRepair();
            persistCheckpoint(build, checkpoint, job);
            return;
        }
        String sourceText = sourceTextByChunk.get(candidate.getChunkIndex());
        if (sourceText == null) throw new IllegalStateException("修复调用检查点对应的来源分块不存在");
        repairCandidate(build, candidate, sourceText, runtime, job, checkpoint, invocation.round());
    }

    private boolean repairCheckpointAlreadyApplied(QuestionBankBuildCandidate candidate, int round) {
        if (candidate.getRepairRound() == null || candidate.getRepairRound() < round) return false;
        String status = String.valueOf(candidate.getRepairStatus()).toUpperCase(Locale.ROOT);
        return Set.of("REPAIRED", "VERIFIED", "DROPPED", "EXHAUSTED").contains(status);
    }

    private QuestionBankBuildSupervisionResult parseSupervisionResponse(String raw) {
        requireNonEmptyResponse(raw, "质量监督");
        return supervisionService.parse(raw);
    }

    private QuestionBankBuildRepairResult parseRepairResponse(QuestionBankBuild build,
                                                               QuestionBankBuildCandidate candidate,
                                                               String raw) {
        return repairService.parse(candidate, raw, buildCategories(build));
    }

    private void requireNonEmptyResponse(String raw, String stage) {
        if (raw == null || raw.isBlank() || "null".equalsIgnoreCase(raw.trim())) {
            throw new IllegalStateException(stage + "返回空响应；请点击重试明确授权再次调用");
        }
    }

    private void applyRepairResult(QuestionBankBuild build,
                                   QuestionBankBuildCandidate candidate,
                                   QuestionBankBuildRepairResult result,
                                   int round) {
        Map<String, Object> before = contentSnapshot(candidate);
        List<String> issues = parseStringList(candidate.getMachineReviewIssuesJson());
        if ("DROP".equals(result.action())) {
            appendRepairHistory(candidate, round, result.summary(), issues, before, null, List.of("排除候选"));
            candidate.setMachineReviewStatus("AUTO_REJECT");
            candidate.setRepairStatus("DROPPED");
            candidate.setMachineReviewScore(0.0);
        } else {
            candidate.setSubject(result.subject()); candidate.setCategory(result.category());
            candidate.setDifficulty(result.difficulty()); candidate.setTagsJson(JSON.toJSONString(result.tags()));
            candidate.setPrinciples(result.principles()); candidate.setPitfalls(result.pitfalls());
            candidate.setFollowUpPathsJson(JSON.toJSONString(result.followUpPaths()));
            validateCandidate(build, candidate);
            Map<String, Object> after = contentSnapshot(candidate);
            appendRepairHistory(candidate, round, result.summary(), issues, before, after, changedFields(before, after));
            candidate.setMachineReviewStatus("PENDING");
            candidate.setMachineReviewScore(null);
            candidate.setMachineReviewIssuesJson(null);
            candidate.setMachineSuggestedPatchJson(null);
            candidate.setDuplicateHint(null);
            candidate.setRepairStatus("REPAIRED");
        }
        candidate.setRepairRound(round);
        candidate.setRepairedAt(LocalDateTime.now());
        QuestionBankBuildCandidate update = new QuestionBankBuildCandidate();
        update.setSubject(candidate.getSubject()); update.setCategory(candidate.getCategory());
        update.setDifficulty(candidate.getDifficulty()); update.setTagsJson(candidate.getTagsJson());
        update.setPrinciples(candidate.getPrinciples()); update.setPitfalls(candidate.getPitfalls());
        update.setFollowUpPathsJson(candidate.getFollowUpPathsJson());
        update.setMachineReviewStatus(candidate.getMachineReviewStatus());
        update.setMachineReviewScore(candidate.getMachineReviewScore());
        update.setMachineReviewIssuesJson(candidate.getMachineReviewIssuesJson());
        update.setMachineSuggestedPatchJson(candidate.getMachineSuggestedPatchJson());
        update.setDuplicateHint(candidate.getDuplicateHint());
        update.setRepairStatus(candidate.getRepairStatus());
        update.setRepairRound(candidate.getRepairRound());
        update.setRepairHistoryJson(candidate.getRepairHistoryJson());
        update.setRepairedAt(candidate.getRepairedAt());
        updatePendingCandidate(candidate, update);
    }

    private void validateCandidate(QuestionBankBuild build, QuestionBankBuildCandidate candidate) {
        List<String> allowed = buildCategories(build);
        if (allowed.isEmpty()) validator.validate(candidate);
        else validator.validate(candidate, allowed);
    }

    private List<String> buildCategories(QuestionBankBuild build) {
        if (build == null || build.getCategoriesJson() == null || build.getCategoriesJson().isBlank()) return List.of();
        try {
            List<String> values = JSON.parseArray(build.getCategoriesJson(), String.class);
            return values == null ? List.of() : values;
        } catch (RuntimeException e) {
            throw new IllegalStateException("构建分类数据损坏，为避免错误归类已停止任务");
        }
    }

    private boolean markRepairRunning(QuestionBankBuildCandidate candidate, int round) {
        candidate.setRepairStatus("RUNNING");
        candidate.setRepairAttempts((candidate.getRepairAttempts() == null ? 0 : candidate.getRepairAttempts()) + 1);
        candidate.setRepairRound(round);
        candidate.setRepairPromptVersion(properties.getRepairPromptVersion());
        QuestionBankBuildCandidate update = new QuestionBankBuildCandidate();
        update.setRepairStatus(candidate.getRepairStatus());
        update.setRepairAttempts(candidate.getRepairAttempts());
        update.setRepairRound(candidate.getRepairRound());
        update.setRepairPromptVersion(candidate.getRepairPromptVersion());
        return updatePendingCandidate(candidate, update);
    }

    private void markRepairFailure(QuestionBankBuildCandidate candidate, int round, String message) {
        Map<String, Object> before = contentSnapshot(candidate);
        appendRepairHistory(candidate, round, message, parseStringList(candidate.getMachineReviewIssuesJson()),
                before, before, List.of());
        candidate.setRepairStatus("FAILED");
        candidate.setRepairRound(round);
        candidate.setMachineReviewStatus("NEEDS_HUMAN");
        candidate.setMachineReviewIssuesJson(JSON.toJSONString(List.of(message)));
        candidate.setRepairedAt(LocalDateTime.now());
        QuestionBankBuildCandidate update = new QuestionBankBuildCandidate();
        update.setRepairStatus(candidate.getRepairStatus());
        update.setRepairRound(candidate.getRepairRound());
        update.setRepairHistoryJson(candidate.getRepairHistoryJson());
        update.setMachineReviewStatus(candidate.getMachineReviewStatus());
        update.setMachineReviewIssuesJson(candidate.getMachineReviewIssuesJson());
        update.setRepairedAt(candidate.getRepairedAt());
        updatePendingCandidate(candidate, update);
    }

    private void updateRepairVerification(QuestionBankBuild build,
                                          Set<Long> candidateScope,
                                          boolean exhausted,
                                          AppJob job) {
        requireJobLease(job);
        for (QuestionBankBuildCandidate candidate : loadCandidates(build.getId())) {
            if (!inScope(candidate, candidateScope)) continue;
            if (candidate.getRepairAttempts() == null || candidate.getRepairAttempts() == 0) continue;
            String next = candidate.getRepairStatus();
            if ("AUTO_PASS".equalsIgnoreCase(candidate.getMachineReviewStatus())) next = "VERIFIED";
            else if ("AUTO_REJECT".equalsIgnoreCase(candidate.getMachineReviewStatus())) next = "DROPPED";
            else if (exhausted && "NEEDS_HUMAN".equalsIgnoreCase(candidate.getMachineReviewStatus())) next = "EXHAUSTED";
            if (!Objects.equals(next, candidate.getRepairStatus())) {
                candidate.setRepairStatus(next);
                QuestionBankBuildCandidate update = new QuestionBankBuildCandidate();
                update.setRepairStatus(next);
                updatePendingCandidate(candidate, update);
            }
        }
        refreshBuildCounts(build, job);
    }

    private boolean canRepair(QuestionBankBuildCandidate candidate) {
        return !hasHumanDecision(candidate)
                && "PENDING".equalsIgnoreCase(candidate.getReviewStatus())
                && "NEEDS_HUMAN".equalsIgnoreCase(candidate.getMachineReviewStatus());
    }

    private boolean hasHumanDecision(QuestionBankBuildCandidate candidate) {
        return "ACCEPTED".equalsIgnoreCase(candidate.getReviewStatus())
                || "REJECTED".equalsIgnoreCase(candidate.getReviewStatus());
    }

    private boolean machineReviewCompleted(QuestionBankBuildCandidate candidate) {
        String status = candidate.getMachineReviewStatus();
        return status != null && Set.of("AUTO_PASS", "NEEDS_HUMAN", "AUTO_REJECT", "SKIPPED")
                .contains(status.toUpperCase(Locale.ROOT));
    }

    private void markMachineSkipped(QuestionBankBuildCandidate candidate) {
        if ("SKIPPED".equalsIgnoreCase(candidate.getMachineReviewStatus())) return;
        candidate.setMachineReviewStatus("SKIPPED");
        candidate.setMachineReviewedAt(LocalDateTime.now());
        updateMachineFields(candidate);
    }

    private boolean markMachineRunning(QuestionBankBuildCandidate candidate) {
        candidate.setMachineReviewStatus("RUNNING");
        candidate.setMachineReviewAttempts((candidate.getMachineReviewAttempts() == null
                ? 0 : candidate.getMachineReviewAttempts()) + 1);
        candidate.setMachineReviewPromptVersion(SUPERVISION_PROMPT_VERSION);
        return updateMachineFields(candidate);
    }

    private void applyMachineResult(QuestionBankBuildCandidate candidate,
                                    QuestionBankBuildSupervisionResult result) {
        candidate.setMachineReviewStatus(result.status());
        candidate.setMachineReviewScore(result.score());
        candidate.setMachineReviewIssuesJson(JSON.toJSONString(result.issues()));
        candidate.setMachineSuggestedPatchJson(JSON.toJSONString(result.suggestedPatch()));
        candidate.setDuplicateHint(result.duplicateHint());
        candidate.setMachineReviewedAt(LocalDateTime.now());
        candidate.setSelfCheckJson(JSON.toJSONString(Map.of(
                "passed", "AUTO_PASS".equals(result.status()),
                "confidence", result.score(),
                "issues", result.issues(),
                "status", result.status()
        )));
        updateMachineFields(candidate);
    }

    private void markMachineNeedsHuman(QuestionBankBuildCandidate candidate, String message) {
        candidate.setMachineReviewStatus("NEEDS_HUMAN");
        candidate.setMachineReviewIssuesJson(JSON.toJSONString(List.of(message == null ? "质量监督失败" : message)));
        candidate.setMachineReviewedAt(LocalDateTime.now());
        if (candidate.getRepairStatus() == null || "NOT_NEEDED".equalsIgnoreCase(candidate.getRepairStatus())) {
            candidate.setRepairStatus("PENDING");
        }
        updateMachineFields(candidate);
    }

    private boolean updateMachineFields(QuestionBankBuildCandidate candidate) {
        QuestionBankBuildCandidate update = new QuestionBankBuildCandidate();
        update.setId(candidate.getId());
        update.setMachineReviewStatus(candidate.getMachineReviewStatus());
        update.setMachineReviewScore(candidate.getMachineReviewScore());
        update.setMachineReviewIssuesJson(candidate.getMachineReviewIssuesJson());
        update.setMachineSuggestedPatchJson(candidate.getMachineSuggestedPatchJson());
        update.setMachineReviewPromptVersion(candidate.getMachineReviewPromptVersion());
        update.setMachineReviewAttempts(candidate.getMachineReviewAttempts());
        update.setMachineReviewedAt(candidate.getMachineReviewedAt());
        update.setDuplicateHint(candidate.getDuplicateHint());
        update.setSelfCheckJson(candidate.getSelfCheckJson());
        update.setRepairStatus(candidate.getRepairStatus());
        return updatePendingCandidate(candidate, update);
    }

    private void refreshBuildCounts(QuestionBankBuild build, AppJob job) {
        List<QuestionBankBuildCandidate> candidates = loadCandidates(build.getId());
        build.setCandidateCount(candidates.size());
        build.setAcceptedCount((int) candidates.stream().filter(item -> "ACCEPTED".equals(item.getReviewStatus())).count());
        build.setRejectedCount((int) candidates.stream().filter(item -> "REJECTED".equals(item.getReviewStatus())).count());
        build.setAutoPassCount((int) candidates.stream().filter(item -> "AUTO_PASS".equals(item.getMachineReviewStatus())).count());
        build.setNeedsHumanCount((int) candidates.stream().filter(item -> "NEEDS_HUMAN".equals(item.getMachineReviewStatus())
                && "PENDING".equals(item.getReviewStatus())).count());
        build.setAutoRejectCount((int) candidates.stream().filter(item -> "AUTO_REJECT".equals(item.getMachineReviewStatus())).count());
        build.setRepairedCount((int) candidates.stream().filter(item -> item.getRepairAttempts() != null
                && item.getRepairAttempts() > 0 && "AUTO_PASS".equals(item.getMachineReviewStatus())).count());
        build.setRepairFailedCount((int) candidates.stream().filter(item -> Set.of("FAILED", "EXHAUSTED")
                .contains(String.valueOf(item.getRepairStatus()).toUpperCase(Locale.ROOT))).count());
        requireJobLease(job);
        QuestionBankBuild update = new QuestionBankBuild();
        update.setId(build.getId());
        update.setCandidateCount(build.getCandidateCount()); update.setAcceptedCount(build.getAcceptedCount());
        update.setRejectedCount(build.getRejectedCount()); update.setAutoPassCount(build.getAutoPassCount());
        update.setNeedsHumanCount(build.getNeedsHumanCount()); update.setAutoRejectCount(build.getAutoRejectCount());
        update.setRepairedCount(build.getRepairedCount()); update.setRepairFailedCount(build.getRepairFailedCount());
        buildMapper.updateById(update);
    }

    private List<QuestionBankBuildCandidate> loadCandidates(Long buildId) {
        return candidateMapper.selectList(new QueryWrapper<QuestionBankBuildCandidate>()
                .eq("build_id", buildId).orderByAsc("chunk_index", "id"));
    }

    private void requireCandidateScope(Long buildId, Set<Long> candidateScope) {
        if (candidateScope == null) return;
        Set<Long> existing = new java.util.LinkedHashSet<>();
        for (QuestionBankBuildCandidate candidate : loadCandidates(buildId)) {
            if (candidateScope.contains(candidate.getId())) existing.add(candidate.getId());
        }
        if (!existing.equals(candidateScope)) {
            throw new IllegalStateException("修复任务候选不存在或不属于当前构建");
        }
    }

    private boolean updatePendingCandidate(QuestionBankBuildCandidate candidate,
                                           QuestionBankBuildCandidate update) {
        int updated = candidateMapper.update(update, new UpdateWrapper<QuestionBankBuildCandidate>()
                .eq("id", candidate.getId())
                .eq("build_id", candidate.getBuildId())
                .eq("review_status", "PENDING"));
        if (updated == 1) return true;
        QuestionBankBuildCandidate current = candidateMapper.selectById(candidate.getId());
        candidate.setReviewStatus(current == null ? "STALE" : current.getReviewStatus());
        return false;
    }

    private void updateProgress(QuestionBankBuild build, AppJob job, String stage, int progress) {
        updateJobProgress(job, stage, progress);
        build.setStage(stage);
        build.setProgress(progress);
        QuestionBankBuild update = new QuestionBankBuild();
        update.setId(build.getId());
        update.setStage(stage);
        update.setProgress(progress);
        update.setRepairRound(build.getRepairRound());
        buildMapper.updateById(update);
        job.setStage(stage);
        job.setProgress(progress);
    }

    private void persistCheckpoint(QuestionBankBuild build,
                                   QuestionBankBuildCheckpoint checkpoint,
                                   AppJob job) {
        requireJobLease(job);
        build.setCheckpointJson(checkpoint.toJson());
        QuestionBankBuild update = new QuestionBankBuild();
        update.setId(build.getId());
        update.setCheckpointJson(build.getCheckpointJson());
        buildMapper.updateById(update);
    }

    private void ensureBuildExecutable(Long buildId) {
        QuestionBankBuild current = buildMapper.selectById(buildId);
        if (current == null || !QuestionBankBuildService.SCOPE_PRIVATE.equalsIgnoreCase(current.getScope())
                || !AppJobService.STATUS_RUNNING.equalsIgnoreCase(current.getStatus())) {
            throw new IllegalStateException("题库构建已不存在或不允许继续执行");
        }
    }

    private void requireJobLease(AppJob job) {
        if (job == null || job.getId() == null || job.getClaimedBy() == null
                || !appJobService.extendRunningJobLease(job.getId(), job.getClaimedBy(), JOB_LEASE_TTL)) {
            throw new QuestionBankBuildLeaseLostException();
        }
    }

    private void updateJobProgress(AppJob job, String stage, int progress) {
        try {
            appJobService.updateRunningJob(job.getId(), job.getClaimedBy(), stage, progress);
        } catch (IllegalStateException e) {
            throw new QuestionBankBuildLeaseLostException(e);
        }
    }

    private boolean inScope(QuestionBankBuildCandidate candidate, Set<Long> candidateScope) {
        return candidateScope == null || candidateScope.contains(candidate.getId());
    }

    private void appendRepairHistory(QuestionBankBuildCandidate candidate,
                                     int round,
                                     String summary,
                                     List<String> issues,
                                     Map<String, Object> before,
                                     Map<String, Object> after,
                                     List<String> changedFields) {
        List<Map<String, Object>> history = new ArrayList<>();
        if (candidate.getRepairHistoryJson() != null && !candidate.getRepairHistoryJson().isBlank()) {
            try {
                for (Map<?, ?> item : JSON.parseArray(candidate.getRepairHistoryJson(), Map.class)) {
                    Map<String, Object> normalized = new LinkedHashMap<>();
                    item.forEach((key, value) -> normalized.put(String.valueOf(key), value));
                    history.add(normalized);
                }
            }
            catch (RuntimeException ignored) { }
        }
        Map<String, Object> record = new LinkedHashMap<>();
        record.put("round", round);
        record.put("summary", summary == null ? "自动修复未返回说明" : summary);
        record.put("issues", issues == null ? List.of() : issues);
        record.put("changedFields", changedFields == null ? List.of() : changedFields);
        record.put("before", before);
        record.put("after", after);
        record.put("repairedAt", LocalDateTime.now().toString());
        history.add(record);
        candidate.setRepairHistoryJson(JSON.toJSONString(history));
    }

    private Map<String, Object> contentSnapshot(QuestionBankBuildCandidate candidate) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("subject", candidate.getSubject()); value.put("category", candidate.getCategory());
        value.put("difficulty", candidate.getDifficulty()); value.put("tags", parseStringList(candidate.getTagsJson()));
        value.put("principles", candidate.getPrinciples()); value.put("pitfalls", candidate.getPitfalls());
        value.put("followUpPaths", parseStringList(candidate.getFollowUpPathsJson()));
        return value;
    }

    private List<String> changedFields(Map<String, Object> before, Map<String, Object> after) {
        if (after == null) return List.of();
        return before.keySet().stream().filter(key -> !Objects.equals(before.get(key), after.get(key))).toList();
    }

    private List<String> parseStringList(String json) {
        if (json == null || json.isBlank()) return List.of();
        try { return JSON.parseArray(json, String.class); } catch (RuntimeException ignored) { return List.of(); }
    }

    private int progress(int start, int end, int completed, int total) {
        return Math.min(end, start + (int) Math.round(completed * (end - start) * 1.0 / Math.max(1, total)));
    }

    private String sanitize(String message) {
        if (message == null || message.isBlank()) return "质量处理失败";
        String sanitized = message.replaceAll(
                "(?i)(Authorization\\s*[:=]\\s*\\S+|Bearer\\s+\\S+|api_key\\s*[:=]\\s*\\S+|sk-[A-Za-z0-9_-]+)",
                "[REDACTED]");
        return sanitized.substring(0, Math.min(300, sanitized.length()));
    }
}
