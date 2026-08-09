package com.interview.service.questionbank.build;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.interview.dto.questionbank.QuestionBankBulkAtomRequest;
import com.interview.entity.AppJob;
import com.interview.entity.QuestionBankBuild;
import com.interview.mapper.QuestionBankBuildMapper;
import com.interview.service.AppJobHandler;
import com.interview.service.AppJobService;
import com.interview.service.questionbank.KnowledgeWorkspaceService;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class QuestionBankBuildFinalizationJobHandler implements AppJobHandler {
    private final QuestionBankBuildMapper buildMapper;
    private final QuestionBankBuildFinalizationPreparationService preparationService;
    private final KnowledgeWorkspaceService workspaceService;
    private final AppJobService appJobService;

    public QuestionBankBuildFinalizationJobHandler(QuestionBankBuildMapper buildMapper,
                                                   QuestionBankBuildFinalizationPreparationService preparationService,
                                                   KnowledgeWorkspaceService workspaceService,
                                                   AppJobService appJobService) {
        this.buildMapper = buildMapper;
        this.preparationService = preparationService;
        this.workspaceService = workspaceService;
        this.appJobService = appJobService;
    }

    @Override
    public String jobType() {
        return QuestionBankBuildFinalizationService.JOB_TYPE;
    }

    @Override
    public void handle(AppJob job) {
        QuestionBankBuild build = requireOwnedPrivateBuild(job);
        try {
            updateProgress(build, job, "FINALIZING", 20);
            List<Long> candidateIds = candidateIds(job.getPayloadJson());
            if (candidateIds.isEmpty()) {
                throw new IllegalArgumentException("终审作业缺少候选原子");
            }
            List<String> atomIds = preparationService.prepare(
                    build.getId(), build.getOwnerUserId(), build.getKnowledgeBaseId(), candidateIds);

            updateProgress(build, job, "PUBLISHING", 80);
            QuestionBankBulkAtomRequest publishRequest = atomRequest(atomIds);
            Map<String, Integer> publishResult = workspaceService.publishAtoms(
                    build.getOwnerUserId(), build.getKnowledgeBaseId(), publishRequest);
            requireAllAtomsMatched(atomIds, publishResult, "发布");

            Map<String, Integer> indexResult = null;
            if (requiresReindex(atomIds, publishResult)) {
                updateProgress(build, job, "INDEXING", 90);
                indexResult = workspaceService.ensureAtomsIndexed(
                        build.getOwnerUserId(), build.getKnowledgeBaseId(), atomRequest(atomIds));
                requireAllAtomsMatched(atomIds, indexResult, "索引");
            }

            Map<String, Object> result = finalResult(atomIds, publishResult, indexResult);
            int failed = number(result, "failed");
            build.setFinalizationResultJson(JSON.toJSONString(result));
            if (failed > 0) {
                markIndexFailure(build, "仍有 " + failed + " 个知识原子未完成发布或索引，可安全重试该作业");
                job.setResultJson(build.getFinalizationResultJson());
                throw new IllegalStateException(build.getErrorMessage());
            }

            build.setStatus(AppJobService.STATUS_COMPLETED);
            build.setStage("PUBLISHED");
            build.setProgress(100);
            build.setFinalizationStatus("COMPLETED");
            build.setErrorMessage(null);
            buildMapper.updateById(build);
            job.setStage("PUBLISHED");
            job.setProgress(100);
            job.setResultJson(build.getFinalizationResultJson());
        } catch (RuntimeException e) {
            if (!"PUBLISHED_WITH_INDEX_ERRORS".equals(build.getStage())) {
                build.setStatus(AppJobService.STATUS_FAILED);
                build.setStage(job.getStage() == null ? "FINALIZING" : job.getStage());
                build.setProgress(100);
                build.setFinalizationStatus("FAILED");
                build.setErrorMessage(sanitize(e.getMessage()));
                buildMapper.updateById(build);
            }
            throw e;
        }
    }

    private QuestionBankBuild requireOwnedPrivateBuild(AppJob job) {
        if (job.getBuildId() == null || job.getOwnerUserId() == null || job.getKnowledgeBaseId() == null) {
            throw new IllegalArgumentException("终审作业缺少作用域信息");
        }
        QuestionBankBuild build = buildMapper.selectById(job.getBuildId());
        if (build == null
                || !QuestionBankBuildService.SCOPE_PRIVATE.equalsIgnoreCase(build.getScope())
                || !job.getOwnerUserId().equals(build.getOwnerUserId())
                || !job.getKnowledgeBaseId().equals(build.getKnowledgeBaseId())) {
            throw new IllegalArgumentException("终审构建不存在或作用域不匹配");
        }
        return build;
    }

    private void updateProgress(QuestionBankBuild build, AppJob job, String stage, int progress) {
        build.setStatus(AppJobService.STATUS_RUNNING);
        build.setStage(stage);
        build.setProgress(progress);
        build.setErrorMessage(null);
        buildMapper.updateById(build);
        job.setStage(stage);
        job.setProgress(progress);
        appJobService.updateRunningJob(job.getId(), job.getClaimedBy(), stage, progress);
    }

    private List<Long> candidateIds(String payloadJson) {
        if (payloadJson == null || payloadJson.isBlank()) return List.of();
        try {
            JSONObject payload = JSON.parseObject(payloadJson);
            List<Long> ids = payload.getList("candidateIds", Long.class);
            return ids == null ? List.of() : ids.stream().filter(java.util.Objects::nonNull).distinct().toList();
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("终审作业参数无效");
        }
    }

    private QuestionBankBulkAtomRequest atomRequest(List<String> atomIds) {
        QuestionBankBulkAtomRequest request = new QuestionBankBulkAtomRequest();
        request.setAtomIds(atomIds);
        return request;
    }

    private boolean requiresReindex(List<String> atomIds, Map<String, Integer> result) {
        return value(result, "failed") > 0
                || value(result, "skipped") > 0
                || value(result, "synced") < atomIds.size();
    }

    private void requireAllAtomsMatched(List<String> atomIds, Map<String, Integer> result, String action) {
        if (result == null || value(result, "matched") != atomIds.size()) {
            throw new IllegalStateException(action + "结果与终审知识原子不一致");
        }
    }

    private Map<String, Object> finalResult(List<String> atomIds,
                                            Map<String, Integer> publishResult,
                                            Map<String, Integer> indexResult) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("matched", atomIds.size());
        result.put("published", value(publishResult, "published"));
        int synced = indexResult == null
                ? value(publishResult, "synced") : value(indexResult, "synced");
        result.put("synced", synced);
        int reportedFailed = indexResult == null
                ? value(publishResult, "failed") : value(indexResult, "failed");
        result.put("failed", Math.max(reportedFailed, atomIds.size() - synced));
        result.put("skipped", indexResult == null
                ? value(publishResult, "skipped") : value(indexResult, "skipped"));
        result.put("atomIds", atomIds);
        return result;
    }

    private void markIndexFailure(QuestionBankBuild build, String message) {
        build.setStatus(AppJobService.STATUS_FAILED);
        build.setStage("PUBLISHED_WITH_INDEX_ERRORS");
        build.setProgress(100);
        build.setFinalizationStatus("PARTIAL_FAILED");
        build.setErrorMessage(message);
        buildMapper.updateById(build);
    }

    private int number(Map<String, Object> result, String key) {
        Object raw = result.get(key);
        return raw instanceof Number number ? number.intValue() : 0;
    }

    private int value(Map<String, Integer> result, String key) {
        return result == null || result.get(key) == null ? 0 : result.get(key);
    }

    private String sanitize(String message) {
        if (message == null || message.isBlank()) return "题库终审发布失败";
        String sanitized = message.replaceAll(
                "(?i)(Authorization\\s*[:=]\\s*\\S+|Bearer\\s+\\S+|api_key\\s*[:=]\\s*\\S+|sk-[A-Za-z0-9_-]+)",
                "[REDACTED]");
        return sanitized.substring(0, Math.min(300, sanitized.length()));
    }
}
