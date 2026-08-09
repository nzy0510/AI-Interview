package com.interview.service.questionbank.build;

import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.interview.config.QuestionBankBuildProperties;
import com.interview.dto.questionbank.build.QuestionBankBuildResponse;
import com.interview.entity.AppJob;
import com.interview.entity.KnowledgeBase;
import com.interview.entity.KnowledgeSourceFile;
import com.interview.entity.QuestionBankBuild;
import com.interview.mapper.AppJobMapper;
import com.interview.mapper.KnowledgeSourceFileMapper;
import com.interview.mapper.QuestionBankBuildMapper;
import com.interview.service.AppJobRecoveryService;
import com.interview.service.AppJobService;
import com.interview.service.UserLlmRuntimeConfig;
import org.springframework.context.annotation.Lazy;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Owns the transactional persistence and dispatch boundary for a build request.
 * Keeping this boundary outside the query/review service prevents a duplicate-key
 * race from marking the caller's transaction rollback-only.
 */
@Service
public class QuestionBankBuildCreationService {
    private static final String SCOPE_PRIVATE = QuestionBankBuildService.SCOPE_PRIVATE;
    private static final String JOB_TYPE = QuestionBankBuildService.JOB_TYPE;

    private final AppJobMapper appJobMapper;
    private final QuestionBankBuildMapper buildMapper;
    private final KnowledgeSourceFileMapper sourceFileMapper;
    private final AppJobService appJobService;
    private final TransactionTemplate transactionTemplate;
    private final QuestionBankBuildProperties properties;
    private final QuestionBankBuildFileStorage storage;
    private final QuestionBankBuildResponseAssembler responseAssembler;
    private final AppJobRecoveryService recoveryService;

    public QuestionBankBuildCreationService(AppJobMapper appJobMapper,
                                            QuestionBankBuildMapper buildMapper,
                                            KnowledgeSourceFileMapper sourceFileMapper,
                                            AppJobService appJobService,
                                            TransactionTemplate transactionTemplate,
                                            QuestionBankBuildProperties properties,
                                            QuestionBankBuildFileStorage storage,
                                            QuestionBankBuildResponseAssembler responseAssembler,
                                            @Lazy AppJobRecoveryService recoveryService) {
        this.appJobMapper = appJobMapper;
        this.buildMapper = buildMapper;
        this.sourceFileMapper = sourceFileMapper;
        this.appJobService = appJobService;
        this.transactionTemplate = transactionTemplate;
        this.properties = properties;
        this.storage = storage;
        this.responseAssembler = responseAssembler;
        this.recoveryService = recoveryService;
    }

    public QuestionBankBuildResponse create(Long userId,
                                            KnowledgeBase knowledgeBase,
                                            UserLlmRuntimeConfig runtime,
                                            List<QuestionBankBuildInputService.PreparedFile> preparedFiles,
                                            List<String> normalizedCategories) {
        String idempotencyKey = idempotencyKey(
                userId,
                knowledgeBase.getId(),
                runtime,
                properties.getPromptVersion(),
                preparedFiles,
                normalizedCategories);
        AppJob existing = findJob(idempotencyKey);
        if (existing != null && existing.getBuildId() != null) {
            QuestionBankBuild existingBuild = buildMapper.selectById(existing.getBuildId());
            if (isOwnedArtifact(existingBuild, userId, knowledgeBase.getId())) {
                return responseAssembler.toResponse(existingBuild, existing.getId());
            }
            throw new IllegalStateException("构建幂等记录与当前用户或题库不匹配");
        }

        QuestionBankBuildResponse response;
        try {
            response = transactionTemplate.execute(status -> persistBuild(
                    userId, knowledgeBase, runtime, preparedFiles, normalizedCategories, idempotencyKey));
        } catch (DuplicateKeyException e) {
            AppJob duplicate = findJob(idempotencyKey);
            if (duplicate != null && duplicate.getBuildId() != null) {
                QuestionBankBuild existingBuild = buildMapper.selectById(duplicate.getBuildId());
                if (isOwnedArtifact(existingBuild, userId, knowledgeBase.getId())) {
                    return responseAssembler.toResponse(existingBuild, duplicate.getId());
                }
            }
            throw e;
        }
        if (response == null) throw new IllegalStateException("题库构建事务未返回结果");
        try {
            recoveryService.dispatchJob(response.getJobId());
        } catch (RuntimeException ignored) {
            // The committed PENDING job remains recoverable by startup/periodic recovery.
        }
        return response;
    }

    private QuestionBankBuildResponse persistBuild(Long userId,
                                                    KnowledgeBase knowledgeBase,
                                                    UserLlmRuntimeConfig runtime,
                                                    List<QuestionBankBuildInputService.PreparedFile> preparedFiles,
                                                    List<String> normalizedCategories,
                                                    String idempotencyKey) {
        QuestionBankBuild build = new QuestionBankBuild();
        build.setScope(SCOPE_PRIVATE);
        build.setOwnerUserId(userId);
        build.setPositionId(knowledgeBase.getPositionId());
        build.setKnowledgeBaseId(knowledgeBase.getId());
        build.setStatus(AppJobService.STATUS_PENDING);
        build.setStage("QUEUED");
        build.setProgress(0);
        build.setCategoriesJson(JSON.toJSONString(normalizedCategories));
        build.setLlmConfigId(runtime.configId());
        build.setLlmProvider(runtime.provider());
        build.setLlmModel(runtime.modelName());
        build.setLlmRuntimeFingerprint(QuestionBankBuildRuntimeSnapshot.fingerprint(runtime));
        build.setPromptVersion(properties.getPromptVersion());
        build.setChunkCount(0);
        build.setCompletedChunkCount(0);
        build.setCandidateCount(0);
        build.setAcceptedCount(0);
        build.setRejectedCount(0);
        build.setAutoPassCount(0);
        build.setNeedsHumanCount(0);
        build.setAutoRejectCount(0);
        build.setRepairRound(0);
        build.setRepairedCount(0);
        build.setRepairFailedCount(0);
        build.setReviewRevision(0L);
        build.setFinalizationStatus("NOT_STARTED");
        build.setCheckpointJson(JSON.toJSONString(List.of()));
        build.setCreatedBy(userId);
        buildMapper.insert(build);

        try {
            persistSourceFiles(userId, build, preparedFiles);
            AppJob job = createPendingJob(userId, build, idempotencyKey);
            return responseAssembler.toResponse(build, job.getId());
        } catch (RuntimeException e) {
            cleanupStorageOnly(build.getId(), e);
            throw e;
        }
    }

    private void persistSourceFiles(Long userId,
                                    QuestionBankBuild build,
                                    List<QuestionBankBuildInputService.PreparedFile> preparedFiles) {
        try {
            for (QuestionBankBuildInputService.PreparedFile prepared : preparedFiles) {
                QuestionBankBuildFileStorage.StoredFile stored = storage.storeOriginal(build.getId(), prepared.file());
                KnowledgeSourceFile sourceFile = toSourceFile(build, prepared, stored, userId);
                sourceFileMapper.insert(sourceFile);
            }
        } catch (IOException e) {
            throw new IllegalStateException("构建源文件保存失败", e);
        }
    }

    private AppJob createPendingJob(Long userId, QuestionBankBuild build, String idempotencyKey) {
        AppJob job = new AppJob();
        job.setJobType(JOB_TYPE);
        job.setIdempotencyKey(idempotencyKey);
        job.setScope(SCOPE_PRIVATE);
        job.setOwnerUserId(userId);
        job.setPositionId(build.getPositionId());
        job.setKnowledgeBaseId(build.getKnowledgeBaseId());
        job.setBuildId(build.getId());
        job.setStatus(AppJobService.STATUS_PENDING);
        job.setStage("QUEUED");
        job.setProgress(0);
        job.setRetryable(false);
        job.setRetryCount(0);
        job.setCreatedBy(userId);
        job.setPayloadJson(JSON.toJSONString(Map.of("buildId", build.getId())));
        appJobService.createPendingJob(job);
        return job;
    }

    private KnowledgeSourceFile toSourceFile(QuestionBankBuild build,
                                             QuestionBankBuildInputService.PreparedFile prepared,
                                             QuestionBankBuildFileStorage.StoredFile stored,
                                             Long userId) {
        KnowledgeSourceFile sourceFile = new KnowledgeSourceFile();
        sourceFile.setScope(SCOPE_PRIVATE);
        sourceFile.setOwnerUserId(userId);
        sourceFile.setPositionId(build.getPositionId());
        sourceFile.setKnowledgeBaseId(build.getKnowledgeBaseId());
        sourceFile.setBuildId(build.getId());
        sourceFile.setOriginalFilename(safeDisplayName(prepared.file().getOriginalFilename()));
        sourceFile.setContentType(prepared.file().getContentType());
        sourceFile.setFileSize(stored.size());
        sourceFile.setFileHash(stored.sha256());
        sourceFile.setStorageKey(stored.storageKey());
        sourceFile.setStatus("UPLOADED");
        sourceFile.setCreatedBy(userId);
        return sourceFile;
    }

    private AppJob findJob(String idempotencyKey) {
        return appJobMapper.selectOne(new QueryWrapper<AppJob>().eq("idempotency_key", idempotencyKey));
    }

    String idempotencyKey(Long userId,
                          Long knowledgeBaseId,
                          UserLlmRuntimeConfig runtime,
                          String promptVersion,
                          List<QuestionBankBuildInputService.PreparedFile> files,
                          List<String> categories) {
        String providerSnapshot = runtime.provider() == null ? "" : runtime.provider().trim().toLowerCase(Locale.ROOT);
        String baseUrlSnapshot = runtime.baseUrl() == null ? "" : runtime.baseUrl().trim();
        String modelSnapshot = runtime.modelName() == null ? "" : runtime.modelName().trim();
        String temperatureSnapshot = runtime.temperature() == null ? "" : runtime.temperature().toString();
        String raw = userId + "|" + knowledgeBaseId + "|" + runtime.configId() + "|"
                + providerSnapshot + "|" + baseUrlSnapshot + "|" + modelSnapshot + "|" + temperatureSnapshot + "|"
                + promptVersion + "|" + categories + "|"
                + files.stream().map(item -> sha256(item.bytes())).collect(Collectors.joining(","));
        return JOB_TYPE + ":" + shortHash(raw);
    }

    private boolean isOwnedArtifact(QuestionBankBuild build, Long userId, Long knowledgeBaseId) {
        return build != null
                && SCOPE_PRIVATE.equalsIgnoreCase(build.getScope())
                && userId.equals(build.getOwnerUserId())
                && knowledgeBaseId.equals(build.getKnowledgeBaseId());
    }

    private String shortHash(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8))).substring(0, 32);
        } catch (Exception e) {
            throw new IllegalStateException("幂等键生成失败", e);
        }
    }

    private String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (Exception e) {
            throw new IllegalStateException("文件哈希计算失败", e);
        }
    }

    private void cleanupStorageOnly(Long buildId, RuntimeException original) {
        try {
            storage.deleteBuild(buildId);
        } catch (IOException cleanupFailure) {
            original.addSuppressed(cleanupFailure);
        }
    }

    private String safeDisplayName(String filename) {
        if (filename == null || filename.isBlank()) return "file";
        String value = filename.replace('\\', '/');
        int slash = value.lastIndexOf('/');
        if (slash >= 0) value = value.substring(slash + 1);
        value = value.replaceAll("[\\p{Cntrl}\\p{Cf}]", "_").trim();
        if (value.isBlank()) return "file";
        return value.substring(Math.max(0, value.length() - Math.min(255, value.length())));
    }
}
