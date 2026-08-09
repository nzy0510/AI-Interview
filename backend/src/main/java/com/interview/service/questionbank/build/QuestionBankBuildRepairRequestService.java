package com.interview.service.questionbank.build;

import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.interview.dto.questionbank.build.QuestionBankBuildRepairRequest;
import com.interview.dto.questionbank.build.QuestionBankBuildResponse;
import com.interview.entity.AppJob;
import com.interview.entity.QuestionBankBuild;
import com.interview.entity.QuestionBankBuildCandidate;
import com.interview.mapper.AppJobMapper;
import com.interview.mapper.QuestionBankBuildCandidateMapper;
import com.interview.mapper.QuestionBankBuildMapper;
import com.interview.service.AppJobRecoveryService;
import com.interview.service.AppJobService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class QuestionBankBuildRepairRequestService {
    private static final int MAX_INSTRUCTION_LENGTH = 500;

    private final QuestionBankBuildService buildService;
    private final QuestionBankBuildMapper buildMapper;
    private final QuestionBankBuildCandidateMapper candidateMapper;
    private final AppJobMapper appJobMapper;
    private final AppJobService appJobService;
    private final AppJobRecoveryService recoveryService;
    private final QuestionBankBuildResponseAssembler assembler;
    private final TransactionTemplate transactionTemplate;

    public QuestionBankBuildRepairRequestService(QuestionBankBuildService buildService,
                                                 QuestionBankBuildMapper buildMapper,
                                                 QuestionBankBuildCandidateMapper candidateMapper,
                                                 AppJobMapper appJobMapper,
                                                 AppJobService appJobService,
                                                 AppJobRecoveryService recoveryService,
                                                 QuestionBankBuildResponseAssembler assembler,
                                                 TransactionTemplate transactionTemplate) {
        this.buildService = buildService;
        this.buildMapper = buildMapper;
        this.candidateMapper = candidateMapper;
        this.appJobMapper = appJobMapper;
        this.appJobService = appJobService;
        this.recoveryService = recoveryService;
        this.assembler = assembler;
        this.transactionTemplate = transactionTemplate;
    }

    public QuestionBankBuildResponse start(Long userId,
                                           Long knowledgeBaseId,
                                           Long buildId,
                                           QuestionBankBuildRepairRequest request) {
        StartResult result = transactionTemplate.execute(status -> persist(
                userId, knowledgeBaseId, buildId, request));
        if (result == null) throw new IllegalStateException("修复任务事务未返回结果");
        if (result.dispatch()) {
            try {
                recoveryService.dispatchJob(result.response().getJobId());
            } catch (RuntimeException ignored) {
                // The committed pending job is recovered by startup/periodic dispatch.
            }
        }
        return result.response();
    }

    private StartResult persist(Long userId,
                                Long knowledgeBaseId,
                                Long buildId,
                                QuestionBankBuildRepairRequest request) {
        QuestionBankBuild build = buildService.requireOwnedBuild(userId, knowledgeBaseId, buildId);
        List<Long> candidateIds = cleanIds(request == null ? null : request.getCandidateIds());
        if (candidateIds.isEmpty()) throw new IllegalArgumentException("至少选择一个需要修复的候选");
        Long expectedRevision = request == null ? null : request.getExpectedReviewRevision();
        if (expectedRevision == null) throw new IllegalArgumentException("缺少审核版本，请刷新后重试");
        String instruction = cleanInstruction(request == null ? null : request.getInstruction());
        String idempotencyKey = idempotencyKey(buildId, expectedRevision + 1);
        AppJob existing = appJobMapper.selectOne(new QueryWrapper<AppJob>()
                .eq("idempotency_key", idempotencyKey).last("LIMIT 1"));
        if (existing != null) {
            if (!Long.valueOf(expectedRevision + 1).equals(build.getReviewRevision())) {
                throw new IllegalStateException("构建内容已变化，请刷新后重新修复");
            }
            requireMatchingExisting(existing, build, candidateIds, instruction);
            return new StartResult(assembler.toResponse(build, existing.getId()), false);
        }
        if (!AppJobService.STATUS_COMPLETED.equalsIgnoreCase(build.getStatus())
                || !"READY_FOR_FINAL_REVIEW".equalsIgnoreCase(build.getStage())) {
            throw new IllegalStateException("构建尚未进入可修复状态");
        }
        if (!expectedRevision.equals(build.getReviewRevision())) {
            throw new IllegalStateException("构建内容已变化，请刷新后重新修复");
        }
        List<QuestionBankBuildCandidate> candidates = candidateMapper.selectList(
                new QueryWrapper<QuestionBankBuildCandidate>()
                        .eq("build_id", buildId)
                        .eq("owner_user_id", userId)
                        .in("id", candidateIds)
                        .orderByAsc("chunk_index", "id"));
        if (candidates.size() != candidateIds.size()) {
            throw new IllegalArgumentException("修复候选不存在或不属于当前构建");
        }
        for (QuestionBankBuildCandidate candidate : candidates) {
            requireRepairable(candidate);
        }

        int claimed = buildMapper.update(null, new UpdateWrapper<QuestionBankBuild>()
                .eq("id", buildId)
                .eq("status", AppJobService.STATUS_COMPLETED)
                .eq("stage", "READY_FOR_FINAL_REVIEW")
                .eq("review_revision", expectedRevision)
                .set("status", AppJobService.STATUS_PENDING)
                .set("stage", "REPAIRING")
                .set("progress", 76)
                .set("repair_round", 0)
                .set("error_message", null)
                .setSql("review_revision = review_revision + 1"));
        if (claimed != 1) throw new IllegalStateException("构建内容已变化，请刷新后重新修复");

        for (QuestionBankBuildCandidate candidate : candidates) {
            candidate.setMachineReviewStatus("NEEDS_HUMAN");
            candidate.setRepairStatus("PENDING");
            candidate.setRepairRound(0);
            candidate.setRepairInstruction(instruction);
            candidateMapper.updateById(candidate);
        }
        QuestionBankBuildCheckpoint checkpoint = QuestionBankBuildCheckpoint.parse(build.getCheckpointJson());
        checkpoint.clearRepair();
        build.setCheckpointJson(checkpoint.toJson());
        QuestionBankBuild checkpointUpdate = new QuestionBankBuild();
        checkpointUpdate.setId(buildId);
        checkpointUpdate.setCheckpointJson(build.getCheckpointJson());
        buildMapper.updateById(checkpointUpdate);

        AppJob job = new AppJob();
        job.setJobType(QuestionBankBuildService.JOB_TYPE);
        job.setIdempotencyKey(idempotencyKey);
        job.setScope(QuestionBankBuildService.SCOPE_PRIVATE);
        job.setOwnerUserId(userId);
        job.setPositionId(build.getPositionId());
        job.setKnowledgeBaseId(knowledgeBaseId);
        job.setBuildId(buildId);
        job.setStage("REPAIRING");
        job.setProgress(76);
        job.setCreatedBy(userId);
        job.setPayloadJson(JSON.toJSONString(Map.of(
                "candidateIds", candidateIds,
                "instruction", instruction == null ? "" : instruction)));
        appJobService.createPendingJob(job);

        build.setStatus(AppJobService.STATUS_PENDING);
        build.setStage("REPAIRING");
        build.setProgress(76);
        build.setRepairRound(0);
        build.setReviewRevision(expectedRevision + 1);
        build.setErrorMessage(null);
        return new StartResult(assembler.toResponse(build, job.getId()), true);
    }

    private void requireRepairable(QuestionBankBuildCandidate candidate) {
        String review = normalized(candidate.getReviewStatus());
        String machine = normalized(candidate.getMachineReviewStatus());
        if (!"PENDING".equals(review)
                || List.of("AUTO_PASS", "AUTO_REJECT", "SKIPPED").contains(machine)) {
            throw new IllegalArgumentException("只能让修复助手处理仍需关注的候选");
        }
    }

    private void requireMatchingExisting(AppJob job,
                                         QuestionBankBuild build,
                                         List<Long> candidateIds,
                                         String instruction) {
        if (!build.getId().equals(job.getBuildId())
                || !build.getOwnerUserId().equals(job.getOwnerUserId())
                || !build.getKnowledgeBaseId().equals(job.getKnowledgeBaseId())) {
            throw new IllegalStateException("已存在的修复任务与当前构建不匹配");
        }
        try {
            var payload = JSON.parseObject(job.getPayloadJson());
            List<Long> existingIds = cleanIds(payload.getList("candidateIds", Long.class));
            String existingInstruction = cleanInstruction(payload.getString("instruction"));
            if (!existingIds.equals(candidateIds)
                    || !java.util.Objects.equals(existingInstruction, instruction)) {
                throw new IllegalStateException("同一审核版本已提交不同的修复任务");
            }
        } catch (IllegalStateException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new IllegalStateException("已存在的修复任务参数损坏");
        }
    }

    private String cleanInstruction(String value) {
        if (value == null || value.isBlank()) return null;
        String cleaned = value.trim();
        if (cleaned.length() > MAX_INSTRUCTION_LENGTH) {
            throw new IllegalArgumentException("补充要求不能超过 500 字");
        }
        return cleaned;
    }

    private List<Long> cleanIds(List<Long> values) {
        if (values == null) return List.of();
        return new LinkedHashSet<>(values.stream().filter(java.util.Objects::nonNull).toList())
                .stream().sorted().toList();
    }

    private String normalized(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }

    private String idempotencyKey(Long buildId, long revision) {
        return "question-bank-assisted-repair:" + buildId + ":" + revision;
    }

    private record StartResult(QuestionBankBuildResponse response, boolean dispatch) {
    }
}
