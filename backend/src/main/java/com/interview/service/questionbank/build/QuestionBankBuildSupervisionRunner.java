package com.interview.service.questionbank.build;

import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.interview.entity.AppJob;
import com.interview.entity.QuestionBankBuild;
import com.interview.entity.QuestionBankBuildCandidate;
import com.interview.mapper.QuestionBankBuildCandidateMapper;
import com.interview.mapper.QuestionBankBuildMapper;
import com.interview.service.AppJobService;
import com.interview.service.UserLlmRuntimeConfig;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

final class QuestionBankBuildSupervisionRunner {
    private static final String PROMPT_VERSION = "qb-supervision-v1";

    private final QuestionBankBuildMapper buildMapper;
    private final QuestionBankBuildCandidateMapper candidateMapper;
    private final AppJobService appJobService;
    private final QuestionBankBuildSupervisionService supervisionService;

    QuestionBankBuildSupervisionRunner(QuestionBankBuildMapper buildMapper,
                                       QuestionBankBuildCandidateMapper candidateMapper,
                                       AppJobService appJobService,
                                       QuestionBankBuildSupervisionService supervisionService) {
        this.buildMapper = buildMapper;
        this.candidateMapper = candidateMapper;
        this.appJobService = appJobService;
        this.supervisionService = supervisionService;
    }

    void run(QuestionBankBuild build,
             Map<Integer, String> sourceTextByChunk,
             UserLlmRuntimeConfig runtime,
             AppJob job) {
        List<QuestionBankBuildCandidate> candidates = candidateMapper.selectList(
                new QueryWrapper<QuestionBankBuildCandidate>()
                        .eq("build_id", build.getId())
                        .orderByAsc("chunk_index", "id"));
        if (candidates.isEmpty()) return;
        updateProgress(build, job, 65);
        int completed = (int) candidates.stream().filter(this::machineReviewCompleted).count();
        for (QuestionBankBuildCandidate candidate : candidates) {
            if (hasHumanDecision(candidate)) {
                if (!machineReviewCompleted(candidate)) completed++;
                markMachineSkipped(candidate);
                continue;
            }
            if (machineReviewCompleted(candidate)) continue;
            if ("RUNNING".equalsIgnoreCase(candidate.getMachineReviewStatus())
                    || "FAILED".equalsIgnoreCase(candidate.getMachineReviewStatus())) {
                markMachineNeedsHuman(candidate, "上次质量监督结果未确认，为避免重复模型调用已转人工处理");
                completed++;
                updateProgress(build, job, Math.min(95, 65 + (int) Math.round(completed * 30.0 / candidates.size())));
                continue;
            }
            ensureBuildExecutable(build.getId());
            String sourceText = sourceTextByChunk.get(candidate.getChunkIndex());
            if (sourceText == null) throw new IllegalStateException("候选原子缺少来源分块");
            markMachineRunning(candidate);
            try {
                QuestionBankBuildSupervisionResult result = supervisionService.review(
                        new QuestionBankBuildSupervisionContext(build, candidate, sourceText, runtime, candidates));
                applyMachineResult(candidate, result);
                refreshBuildCounts(build);
            } catch (RuntimeException e) {
                markMachineNeedsHuman(candidate, sanitize(e.getMessage()));
                refreshBuildCounts(build);
            }
            completed++;
            updateProgress(build, job, Math.min(95, 65 + (int) Math.round(completed * 30.0 / candidates.size())));
        }
        refreshBuildCounts(build);
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

    private void markMachineRunning(QuestionBankBuildCandidate candidate) {
        candidate.setMachineReviewStatus("RUNNING");
        candidate.setMachineReviewAttempts((candidate.getMachineReviewAttempts() == null
                ? 0 : candidate.getMachineReviewAttempts()) + 1);
        candidate.setMachineReviewPromptVersion(PROMPT_VERSION);
        updateMachineFields(candidate);
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
        updateMachineFields(candidate);
    }

    private void updateMachineFields(QuestionBankBuildCandidate candidate) {
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
        candidateMapper.updateById(update);
    }

    private void updateProgress(QuestionBankBuild build, AppJob job, int progress) {
        build.setStage("SUPERVISING");
        build.setProgress(progress);
        buildMapper.updateById(build);
        job.setStage("SUPERVISING");
        job.setProgress(progress);
        appJobService.updateRunningJob(job.getId(), job.getClaimedBy(), "SUPERVISING", progress);
    }

    private void ensureBuildExecutable(Long buildId) {
        QuestionBankBuild current = buildMapper.selectById(buildId);
        if (current == null || !QuestionBankBuildService.SCOPE_PRIVATE.equalsIgnoreCase(current.getScope())
                || !AppJobService.STATUS_RUNNING.equalsIgnoreCase(current.getStatus())) {
            throw new IllegalStateException("题库构建已不存在或不允许继续执行");
        }
    }

    private void refreshBuildCounts(QuestionBankBuild build) {
        List<QuestionBankBuildCandidate> candidates = candidateMapper.selectList(
                new QueryWrapper<QuestionBankBuildCandidate>().eq("build_id", build.getId()));
        build.setCandidateCount(candidates.size());
        build.setAcceptedCount((int) candidates.stream().filter(item -> "ACCEPTED".equals(item.getReviewStatus())).count());
        build.setRejectedCount((int) candidates.stream().filter(item -> "REJECTED".equals(item.getReviewStatus())).count());
        build.setAutoPassCount((int) candidates.stream().filter(item -> "AUTO_PASS".equals(item.getMachineReviewStatus())).count());
        build.setNeedsHumanCount((int) candidates.stream().filter(item -> "NEEDS_HUMAN".equals(item.getMachineReviewStatus())
                && "PENDING".equals(item.getReviewStatus())).count());
        build.setAutoRejectCount((int) candidates.stream().filter(item -> "AUTO_REJECT".equals(item.getMachineReviewStatus())).count());
        buildMapper.updateById(build);
    }

    private String sanitize(String message) {
        if (message == null || message.isBlank()) return "质量监督失败";
        String sanitized = message.replaceAll(
                "(?i)(Authorization\\s*[:=]\\s*\\S+|Bearer\\s+\\S+|api_key\\s*[:=]\\s*\\S+|sk-[A-Za-z0-9_-]+)",
                "[REDACTED]");
        return sanitized.substring(0, Math.min(300, sanitized.length()));
    }
}
