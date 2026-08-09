package com.interview.service.questionbank.build;

import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.interview.dto.questionbank.build.QuestionBankBuildFinalizationRequest;
import com.interview.dto.questionbank.build.QuestionBankBuildFinalizationResponse;
import com.interview.entity.AppJob;
import com.interview.entity.QuestionBankBuild;
import com.interview.entity.QuestionBankBuildCandidate;
import com.interview.mapper.AppJobMapper;
import com.interview.mapper.QuestionBankBuildCandidateMapper;
import com.interview.mapper.QuestionBankBuildMapper;
import com.interview.service.AppJobRecoveryService;
import com.interview.service.AppJobService;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class QuestionBankBuildFinalizationService {
    public static final String JOB_TYPE = "FINALIZE_QUESTION_BANK_BUILD";

    private final QuestionBankBuildService buildService;
    private final QuestionBankBuildMapper buildMapper;
    private final QuestionBankBuildCandidateMapper candidateMapper;
    private final AppJobMapper appJobMapper;
    private final AppJobService appJobService;
    private final TransactionTemplate transactionTemplate;
    private final AppJobRecoveryService recoveryService;

    public QuestionBankBuildFinalizationService(QuestionBankBuildService buildService,
                                                QuestionBankBuildMapper buildMapper,
                                                QuestionBankBuildCandidateMapper candidateMapper,
                                                AppJobMapper appJobMapper,
                                                AppJobService appJobService,
                                                TransactionTemplate transactionTemplate,
                                                AppJobRecoveryService recoveryService) {
        this.buildService = buildService;
        this.buildMapper = buildMapper;
        this.candidateMapper = candidateMapper;
        this.appJobMapper = appJobMapper;
        this.appJobService = appJobService;
        this.transactionTemplate = transactionTemplate;
        this.recoveryService = recoveryService;
    }

    public QuestionBankBuildFinalizationResponse start(Long userId,
                                                       Long knowledgeBaseId,
                                                       Long buildId,
                                                       QuestionBankBuildFinalizationRequest request) {
        StartResult result;
        try {
            result = transactionTemplate.execute(status -> persist(
                    userId, knowledgeBaseId, buildId, request));
        } catch (DuplicateKeyException e) {
            result = existingResult(userId, knowledgeBaseId, buildId, cleanIds(
                    request == null ? null : request.getCandidateIds()));
            if (result == null) throw e;
        }
        if (result == null) throw new IllegalStateException("题库终审事务未返回结果");
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
                                QuestionBankBuildFinalizationRequest request) {
        QuestionBankBuild build = buildService.requireOwnedBuild(userId, knowledgeBaseId, buildId);
        List<Long> selectedIds = cleanIds(request == null ? null : request.getCandidateIds());
        if (selectedIds.isEmpty()) throw new IllegalArgumentException("终审至少需要选择一个候选原子");
        StartResult existing = existingResult(build, selectedIds);
        if (existing != null) return existing;
        if (!AppJobService.STATUS_COMPLETED.equalsIgnoreCase(build.getStatus())
                || !"READY_FOR_FINAL_REVIEW".equalsIgnoreCase(build.getStage())) {
            throw new IllegalStateException("构建尚未进入可终审状态");
        }
        if (request == null || request.getExpectedReviewRevision() == null
                || build.getReviewRevision() == null
                || !build.getReviewRevision().equals(request.getExpectedReviewRevision())) {
            throw new IllegalStateException("构建内容已变化，请刷新后重新终审");
        }
        List<QuestionBankBuildCandidate> candidates = candidateMapper.selectList(
                new QueryWrapper<QuestionBankBuildCandidate>()
                        .eq("build_id", buildId)
                        .eq("owner_user_id", userId)
                        .orderByAsc("chunk_index", "id"));
        rejectUnresolvedCandidates(candidates);
        List<QuestionBankBuildCandidate> selected = candidates.stream()
                .filter(candidate -> selectedIds.contains(candidate.getId()))
                .toList();
        if (selected.size() != selectedIds.size()) throw new IllegalArgumentException("终审候选不存在或不属于当前构建");
        if (selected.stream().anyMatch(candidate -> "REJECTED".equalsIgnoreCase(candidate.getReviewStatus()))) {
            throw new IllegalArgumentException("终审不能包含人工拒绝的候选");
        }
        if (selected.stream().anyMatch(candidate -> !canFinalize(candidate))) {
            throw new IllegalArgumentException("终审包含尚未通过机器监督或人工接受的候选");
        }

        String idempotencyKey = "question-bank-finalize:" + buildId;
        int claimed = buildMapper.update(null, new UpdateWrapper<QuestionBankBuild>()
                .eq("id", buildId)
                .eq("status", AppJobService.STATUS_COMPLETED)
                .eq("stage", "READY_FOR_FINAL_REVIEW")
                .eq("review_revision", request.getExpectedReviewRevision())
                .set("status", AppJobService.STATUS_PENDING)
                .set("stage", "FINALIZING")
                .set("progress", 0)
                .set("finalization_status", "NOT_STARTED")
                .set("finalized_by", userId)
                .set("error_message", null)
                .setSql("review_revision = review_revision + 1"));
        if (claimed != 1) throw new IllegalStateException("构建内容已变化，请刷新后重新终审");

        AppJob job = new AppJob();
        job.setJobType(JOB_TYPE);
        job.setIdempotencyKey(idempotencyKey);
        job.setScope(build.getScope());
        job.setOwnerUserId(userId);
        job.setPositionId(build.getPositionId());
        job.setKnowledgeBaseId(knowledgeBaseId);
        job.setBuildId(buildId);
        job.setCreatedBy(userId);
        job.setStage("FINALIZING");
        job.setPayloadJson(JSON.toJSONString(Map.of("candidateIds", selectedIds)));
        appJobService.createPendingJob(job);

        build.setStatus(AppJobService.STATUS_PENDING);
        build.setStage("FINALIZING");
        build.setProgress(0);
        build.setFinalizationStatus("NOT_STARTED");
        build.setFinalizedBy(userId);
        build.setErrorMessage(null);
        build.setReviewRevision(request.getExpectedReviewRevision() + 1);
        return new StartResult(response(build, job.getId(), selectedIds.size()), true);
    }

    private StartResult existingResult(Long userId,
                                       Long knowledgeBaseId,
                                       Long buildId,
                                       List<Long> selectedIds) {
        QuestionBankBuild build = buildService.requireOwnedBuild(userId, knowledgeBaseId, buildId);
        return existingResult(build, selectedIds);
    }

    private StartResult existingResult(QuestionBankBuild build, List<Long> selectedIds) {
        AppJob existing = appJobMapper.selectOne(new QueryWrapper<AppJob>()
                .eq("idempotency_key", "question-bank-finalize:" + build.getId())
                .last("LIMIT 1"));
        if (existing == null) return null;
        List<Long> persistedIds = payloadCandidateIds(existing);
        if (!persistedIds.equals(selectedIds)) {
            throw new IllegalStateException("本批次终审选择与已提交任务不一致");
        }
        return new StartResult(response(build, existing.getId(), persistedIds.size()), false);
    }

    private void rejectUnresolvedCandidates(List<QuestionBankBuildCandidate> candidates) {
        boolean unresolved = candidates.stream().anyMatch(candidate -> {
            if ("ACCEPTED".equalsIgnoreCase(candidate.getReviewStatus())
                    || "REJECTED".equalsIgnoreCase(candidate.getReviewStatus())) return false;
            String status = String.valueOf(candidate.getMachineReviewStatus()).toUpperCase();
            return !Set.of("AUTO_PASS", "AUTO_REJECT", "SKIPPED").contains(status);
        });
        if (unresolved) throw new IllegalStateException("仍有需要人工处理或监督失败的候选，不能终审发布");
    }

    private boolean canFinalize(QuestionBankBuildCandidate candidate) {
        if ("REJECTED".equalsIgnoreCase(candidate.getReviewStatus())) return false;
        return "ACCEPTED".equalsIgnoreCase(candidate.getReviewStatus())
                || ("PENDING".equalsIgnoreCase(candidate.getReviewStatus())
                && "AUTO_PASS".equalsIgnoreCase(candidate.getMachineReviewStatus()));
    }

    private List<Long> payloadCandidateIds(AppJob job) {
        if (job.getPayloadJson() == null || job.getPayloadJson().isBlank()) return List.of();
        try {
            List<Long> ids = JSON.parseObject(job.getPayloadJson()).getList("candidateIds", Long.class);
            return cleanIds(ids);
        } catch (RuntimeException e) {
            throw new IllegalStateException("已提交终审任务参数损坏");
        }
    }

    private List<Long> cleanIds(List<Long> ids) {
        if (ids == null) return List.of();
        return new LinkedHashSet<>(ids.stream().filter(java.util.Objects::nonNull).toList()).stream().toList();
    }

    private QuestionBankBuildFinalizationResponse response(QuestionBankBuild build,
                                                           Long jobId,
                                                           int selectedCount) {
        QuestionBankBuildFinalizationResponse response = new QuestionBankBuildFinalizationResponse();
        response.setBuildId(build.getId());
        response.setJobId(jobId);
        response.setStatus(build.getStatus());
        response.setStage(build.getStage());
        response.setFinalizationStatus(build.getFinalizationStatus());
        response.setSelectedCount(selectedCount);
        return response;
    }

    private record StartResult(QuestionBankBuildFinalizationResponse response, boolean dispatch) {
    }
}
