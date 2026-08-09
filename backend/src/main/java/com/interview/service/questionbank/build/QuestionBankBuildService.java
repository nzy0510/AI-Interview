package com.interview.service.questionbank.build;

import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.interview.config.QuestionBankBuildProperties;
import com.interview.dto.questionbank.QuestionBankImportRequest;
import com.interview.dto.questionbank.build.QuestionBankBuildCandidateResponse;
import com.interview.dto.questionbank.build.QuestionBankBuildCandidateReviewRequest;
import com.interview.dto.questionbank.build.QuestionBankBuildResponse;
import com.interview.entity.AppJob;
import com.interview.entity.KnowledgeBase;
import com.interview.entity.KnowledgeSourceFile;
import com.interview.entity.QuestionBankBuild;
import com.interview.entity.QuestionBankBuildCandidate;
import com.interview.mapper.AppJobMapper;
import com.interview.mapper.KnowledgeSourceFileMapper;
import com.interview.mapper.QuestionBankBuildCandidateMapper;
import com.interview.mapper.QuestionBankBuildMapper;
import com.interview.service.AppJobService;
import com.interview.service.UserLlmConfigService;
import com.interview.service.UserLlmRuntimeConfig;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

@Service
public class QuestionBankBuildService {
    public static final String JOB_TYPE = "GENERATE_QUESTION_BANK_PACKAGE";
    public static final String SCOPE_PRIVATE = "PRIVATE";

    private final KnowledgeSourceFileMapper sourceFileMapper;
    private final QuestionBankBuildMapper buildMapper;
    private final QuestionBankBuildCandidateMapper candidateMapper;
    private final AppJobMapper appJobMapper;
    private final UserLlmConfigService userLlmConfigService;
    private final QuestionBankBuildProperties properties;
    private final QuestionBankBuildInputService inputService;
    private final QuestionBankBuildFileStorage storage;
    private final QuestionBankBuildAccessService accessService;
    private final QuestionBankBuildResponseAssembler responseAssembler;
    private final QuestionBankBuildCreationService creationService;
    private final QuestionBankBuildCandidateValidator candidateValidator;

    public QuestionBankBuildService(KnowledgeSourceFileMapper sourceFileMapper,
                                    QuestionBankBuildMapper buildMapper,
                                    QuestionBankBuildCandidateMapper candidateMapper,
                                    AppJobMapper appJobMapper,
                                    UserLlmConfigService userLlmConfigService,
                                    QuestionBankBuildProperties properties,
                                    QuestionBankBuildInputService inputService,
                                    QuestionBankBuildFileStorage storage,
                                    QuestionBankBuildAccessService accessService,
                                    QuestionBankBuildResponseAssembler responseAssembler,
                                    QuestionBankBuildCreationService creationService,
                                    QuestionBankBuildCandidateValidator candidateValidator) {
        this.sourceFileMapper = sourceFileMapper;
        this.buildMapper = buildMapper;
        this.candidateMapper = candidateMapper;
        this.appJobMapper = appJobMapper;
        this.userLlmConfigService = userLlmConfigService;
        this.properties = properties;
        this.inputService = inputService;
        this.storage = storage;
        this.accessService = accessService;
        this.responseAssembler = responseAssembler;
        this.creationService = creationService;
        this.candidateValidator = candidateValidator;
    }

    public QuestionBankBuildResponse create(Long userId,
                                            Long knowledgeBaseId,
                                            List<MultipartFile> files,
                                            List<String> categories) {
        KnowledgeBase knowledgeBase = accessService.requireBuildTarget(userId, knowledgeBaseId);
        UserLlmRuntimeConfig runtime = userLlmConfigService.requireActiveRuntimeConfig(userId);
        List<QuestionBankBuildInputService.PreparedFile> preparedFiles = inputService.prepare(files);
        List<String> normalizedCategories = normalizeCategories(categories);
        return creationService.create(userId, knowledgeBase, runtime, preparedFiles, normalizedCategories);
    }

    public List<QuestionBankBuildResponse> list(Long userId, Long knowledgeBaseId) {
        accessService.requireBuildTarget(userId, knowledgeBaseId);
        return buildMapper.selectList(new QueryWrapper<QuestionBankBuild>()
                        .eq("knowledge_base_id", knowledgeBaseId)
                        .eq("owner_user_id", userId)
                        .ne("status", "DELETED")
                        .orderByDesc("create_time", "id"))
                .stream().map(build -> responseAssembler.toResponse(build, responseAssembler.jobIdFor(build.getId()))).toList();
    }

    public QuestionBankBuildResponse detail(Long userId, Long knowledgeBaseId, Long buildId) {
        accessService.requireBuildTarget(userId, knowledgeBaseId);
        QuestionBankBuild build = requireBuild(userId, knowledgeBaseId, buildId);
        return responseAssembler.toResponse(build, responseAssembler.jobIdFor(buildId));
    }

    @Transactional
    public void delete(Long userId, Long knowledgeBaseId, Long buildId) {
        accessService.requireBuildTarget(userId, knowledgeBaseId);
        QuestionBankBuild build = requireBuild(userId, knowledgeBaseId, buildId);
        if (AppJobService.STATUS_PENDING.equalsIgnoreCase(build.getStatus())
                || AppJobService.STATUS_RUNNING.equalsIgnoreCase(build.getStatus())) {
            throw new IllegalStateException("构建正在执行，不能删除");
        }
        if (build.getFinalAtomIdsJson() != null && !build.getFinalAtomIdsJson().isBlank()) {
            throw new IllegalStateException("已进入终审发布的构建需要保留来源记录，不能删除");
        }
        int claimed = buildMapper.update(null, new UpdateWrapper<QuestionBankBuild>()
                .eq("id", buildId)
                .in("status", AppJobService.STATUS_COMPLETED, AppJobService.STATUS_FAILED)
                .set("status", "DELETING"));
        if (claimed != 1) throw new IllegalStateException("构建状态已变化，不能删除");
        AppJob latestJob = appJobMapper.selectOne(new QueryWrapper<AppJob>()
                .eq("build_id", buildId).orderByDesc("id").last("LIMIT 1"));
        if (latestJob != null && (AppJobService.STATUS_PENDING.equalsIgnoreCase(latestJob.getStatus())
                || AppJobService.STATUS_RUNNING.equalsIgnoreCase(latestJob.getStatus()))) {
            throw new IllegalStateException("构建任务正在执行或等待重试，不能删除");
        }
        candidateMapper.delete(new QueryWrapper<QuestionBankBuildCandidate>().eq("build_id", buildId));
        sourceFileMapper.delete(new QueryWrapper<KnowledgeSourceFile>().eq("build_id", buildId));
        appJobMapper.delete(new QueryWrapper<AppJob>().eq("build_id", buildId));
        buildMapper.deleteById(buildId);
        try {
            storage.deleteBuild(buildId);
        } catch (IOException e) {
            throw new IllegalStateException("构建源文件清理失败", e);
        }
    }

    @Transactional
    public void deleteForPosition(Long userId, Long positionId) {
        List<QuestionBankBuild> builds = buildMapper.selectList(new QueryWrapper<QuestionBankBuild>()
                .eq("owner_user_id", userId)
                .eq("position_id", positionId)
                .eq("scope", SCOPE_PRIVATE));
        if (builds.isEmpty()) return;
        long activeJobs = appJobMapper.selectCount(new QueryWrapper<AppJob>()
                .eq("owner_user_id", userId)
                .eq("position_id", positionId)
                .in("status", AppJobService.STATUS_PENDING, AppJobService.STATUS_RUNNING));
        if (activeJobs > 0) {
            throw new IllegalStateException("岗位仍有题库任务正在处理，请完成或等待失败后再删除");
        }
        List<Long> buildIds = builds.stream().map(QuestionBankBuild::getId).filter(Objects::nonNull).toList();
        try {
            for (Long buildId : buildIds) storage.deleteBuild(buildId);
        } catch (IOException e) {
            throw new IllegalStateException("岗位源文档清理失败，已停止删除", e);
        }
        if (buildIds.isEmpty()) return;
        candidateMapper.delete(new QueryWrapper<QuestionBankBuildCandidate>().in("build_id", buildIds));
        sourceFileMapper.delete(new QueryWrapper<KnowledgeSourceFile>().in("build_id", buildIds));
        appJobMapper.delete(new QueryWrapper<AppJob>().in("build_id", buildIds));
        buildMapper.delete(new QueryWrapper<QuestionBankBuild>().in("id", buildIds));
    }

    public List<QuestionBankBuildCandidateResponse> candidates(Long userId, Long knowledgeBaseId, Long buildId) {
        accessService.requireBuildTarget(userId, knowledgeBaseId);
        requireBuild(userId, knowledgeBaseId, buildId);
        return candidateMapper.selectList(new QueryWrapper<QuestionBankBuildCandidate>()
                        .eq("build_id", buildId)
                        .eq("owner_user_id", userId)
                        .orderByAsc("chunk_index", "id"))
                .stream().map(responseAssembler::toCandidateResponse).toList();
    }

    @Transactional
    public QuestionBankBuildCandidateResponse review(Long userId,
                                                     Long knowledgeBaseId,
                                                     Long buildId,
                                                     Long candidateId,
                                                     QuestionBankBuildCandidateReviewRequest request) {
        accessService.requireBuildTarget(userId, knowledgeBaseId);
        QuestionBankBuild build = requireBuild(userId, knowledgeBaseId, buildId);
        requireCompletedBuild(build);
        if (!Set.of("READY_FOR_REVIEW", "READY_FOR_FINAL_REVIEW")
                .contains(String.valueOf(build.getStage()).toUpperCase(Locale.ROOT))) {
            throw new IllegalStateException("构建已进入终审阶段，候选内容不允许继续修改");
        }
        QuestionBankBuildCandidate candidate = candidateMapper.selectOne(new QueryWrapper<QuestionBankBuildCandidate>()
                .eq("id", candidateId).eq("build_id", buildId).eq("owner_user_id", userId));
        if (candidate == null) throw new RuntimeException("候选原子不存在或无权访问");
        String action = request == null || request.getAction() == null ? "" : request.getAction().trim().toUpperCase(Locale.ROOT);
        if (!Set.of("ACCEPT", "REJECT", "SAVE").contains(action)) throw new IllegalArgumentException("审核动作必须是 ACCEPT、REJECT 或 SAVE");
        applyEdits(candidate, request);
        if ("ACCEPT".equals(action)) {
            candidateValidator.validate(candidate);
            candidate.setReviewStatus("ACCEPTED");
            candidate.setReviewReason("人工审核接受");
        } else if ("REJECT".equals(action)) {
            candidate.setReviewStatus("REJECTED");
            candidate.setReviewReason("人工审核拒绝");
        } else {
            candidate.setReviewStatus("PENDING");
            candidate.setReviewReason("人工修改已保存，等待接受或拒绝");
            candidate.setMachineReviewStatus("NEEDS_HUMAN");
            candidate.setMachineReviewIssuesJson(JSON.toJSONString(List.of("内容经人工修改，原自动监督结论已失效")));
        }
        claimReviewRevision(build, request.getExpectedReviewRevision());
        candidateMapper.updateById(candidate);
        refreshCounts(buildId);
        return responseAssembler.toCandidateResponse(candidate);
    }

    public QuestionBankImportRequest packageFor(Long userId, Long knowledgeBaseId, Long buildId) {
        accessService.requireBuildTarget(userId, knowledgeBaseId);
        requireBuild(userId, knowledgeBaseId, buildId);
        throw new IllegalStateException("受控文档入库批次不再支持导出候选包，请处理完监督异常后执行整批终审发布");
    }

    private void applyEdits(QuestionBankBuildCandidate candidate, QuestionBankBuildCandidateReviewRequest request) {
        if (request == null) return;
        if (request.getSubject() != null) candidate.setSubject(request.getSubject().trim());
        if (request.getCategory() != null) candidate.setCategory(request.getCategory().trim());
        if (request.getDifficulty() != null) candidate.setDifficulty(request.getDifficulty().trim());
        if (request.getTags() != null) candidate.setTagsJson(JSON.toJSONString(request.getTags()));
        if (request.getPrinciples() != null) candidate.setPrinciples(request.getPrinciples().trim());
        if (request.getPitfalls() != null) candidate.setPitfalls(request.getPitfalls().trim());
        if (request.getFollowUpPaths() != null) candidate.setFollowUpPathsJson(JSON.toJSONString(request.getFollowUpPaths()));
    }

    QuestionBankBuild requireOwnedBuild(Long userId, Long knowledgeBaseId, Long buildId) {
        accessService.requireBuildTarget(userId, knowledgeBaseId);
        return requireBuild(userId, knowledgeBaseId, buildId);
    }

    private void claimReviewRevision(QuestionBankBuild build, Long expectedRevision) {
        long revision = build.getReviewRevision() == null ? 0L : build.getReviewRevision();
        if (expectedRevision == null || expectedRevision != revision) {
            throw new IllegalStateException("构建内容已变化，请刷新后重新审核");
        }
        int claimed = buildMapper.update(null, new UpdateWrapper<QuestionBankBuild>()
                .eq("id", build.getId())
                .eq("status", AppJobService.STATUS_COMPLETED)
                .in("stage", "READY_FOR_REVIEW", "READY_FOR_FINAL_REVIEW")
                .eq("review_revision", expectedRevision)
                .setSql("review_revision = review_revision + 1"));
        if (claimed != 1) throw new IllegalStateException("构建内容已变化，请刷新后重新审核");
        build.setReviewRevision(revision + 1);
    }

    private void refreshCounts(Long buildId) {
        List<QuestionBankBuildCandidate> candidates = candidateMapper.selectList(new QueryWrapper<QuestionBankBuildCandidate>().eq("build_id", buildId));
        QuestionBankBuild update = new QuestionBankBuild();
        update.setId(buildId);
        update.setCandidateCount(candidates.size());
        update.setAcceptedCount((int) candidates.stream().filter(item -> "ACCEPTED".equals(item.getReviewStatus())).count());
        update.setRejectedCount((int) candidates.stream().filter(item -> "REJECTED".equals(item.getReviewStatus())).count());
        update.setAutoPassCount((int) candidates.stream().filter(item -> "AUTO_PASS".equals(item.getMachineReviewStatus())).count());
        update.setNeedsHumanCount((int) candidates.stream().filter(item -> "NEEDS_HUMAN".equals(item.getMachineReviewStatus())
                && "PENDING".equals(item.getReviewStatus())).count());
        update.setAutoRejectCount((int) candidates.stream().filter(item -> "AUTO_REJECT".equals(item.getMachineReviewStatus())).count());
        update.setRepairedCount((int) candidates.stream().filter(item -> Set.of("REPAIRED", "VERIFIED")
                .contains(String.valueOf(item.getRepairStatus()).toUpperCase(Locale.ROOT))).count());
        update.setRepairFailedCount((int) candidates.stream().filter(item -> Set.of("FAILED", "EXHAUSTED")
                .contains(String.valueOf(item.getRepairStatus()).toUpperCase(Locale.ROOT))).count());
        buildMapper.updateById(update);
    }

    private QuestionBankBuild requireBuild(Long userId, Long knowledgeBaseId, Long buildId) {
        QuestionBankBuild build = buildMapper.selectById(buildId);
        if (build == null || !knowledgeBaseId.equals(build.getKnowledgeBaseId()) || !userId.equals(build.getOwnerUserId()) || !SCOPE_PRIVATE.equalsIgnoreCase(build.getScope()) || "DELETED".equalsIgnoreCase(build.getStatus())) {
            throw new RuntimeException("构建不存在或无权访问");
        }
        return build;
    }

    private void requireCompletedBuild(QuestionBankBuild build) {
        if (!AppJobService.STATUS_COMPLETED.equalsIgnoreCase(build.getStatus())) {
            throw new IllegalStateException("题库构建尚未完成，不能审核或导入候选");
        }
    }

    private List<String> normalizeCategories(List<String> categories) {
        if (categories == null) return List.of("通用");
        List<String> normalized = categories.stream().filter(Objects::nonNull).flatMap(value -> java.util.Arrays.stream(value.split(",")))
                .map(String::trim).filter(value -> !value.isBlank()).distinct().limit(20).toList();
        return normalized.isEmpty() ? List.of("通用") : normalized;
    }
}
