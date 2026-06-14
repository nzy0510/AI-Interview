package com.interview.service.questionbank;

import com.alibaba.fastjson2.JSON;
import com.interview.entity.AppJob;
import com.interview.entity.KnowledgeSourceFile;
import com.interview.mapper.AppJobMapper;
import com.interview.mapper.KnowledgeSourceFileMapper;
import com.interview.service.AdminRoleService;
import com.interview.service.AppJobRecoveryService;
import com.interview.service.AppJobService;
import com.interview.service.UserLlmConfigService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@Service
public class KnowledgeAtomJobService {

    private final KnowledgeSourceFileMapper sourceFileMapper;
    private final AppJobMapper appJobMapper;
    private final AppJobRecoveryService appJobRecoveryService;
    private final UserLlmConfigService userLlmConfigService;
    private final AdminRoleService adminRoleService;

    public KnowledgeAtomJobService(KnowledgeSourceFileMapper sourceFileMapper,
                                   AppJobMapper appJobMapper,
                                   AppJobRecoveryService appJobRecoveryService,
                                   UserLlmConfigService userLlmConfigService,
                                   AdminRoleService adminRoleService) {
        this.sourceFileMapper = sourceFileMapper;
        this.appJobMapper = appJobMapper;
        this.appJobRecoveryService = appJobRecoveryService;
        this.userLlmConfigService = userLlmConfigService;
        this.adminRoleService = adminRoleService;
    }

    @Transactional
    public KnowledgeAtomGenerationResult createGenerationJob(Long sourceFileId, Long currentUserId) {
        KnowledgeSourceFile sourceFile = requireVisibleConvertedSourceFile(sourceFileId, currentUserId);
        userLlmConfigService.requireActiveRuntimeConfig(currentUserId);

        AppJob job = new AppJob();
        job.setJobType(KnowledgeAtomWorkflowService.JOB_TYPE_GENERATE_ATOMS);
        job.setScope(sourceFile.getScope());
        job.setOwnerUserId(sourceFile.getOwnerUserId());
        job.setPositionId(sourceFile.getPositionId());
        job.setKnowledgeBaseId(sourceFile.getKnowledgeBaseId());
        job.setSourceFileId(sourceFile.getId());
        job.setStatus(AppJobService.STATUS_PENDING);
        job.setStage("GENERATE_ATOMS");
        job.setProgress(0);
        job.setRetryable(false);
        job.setRetryCount(0);
        job.setCreatedBy(currentUserId);
        job.setPayloadJson(JSON.toJSONString(Map.of("sourceFileId", sourceFile.getId())));
        appJobMapper.insert(job);
        appJobRecoveryService.dispatchJob(job.getId());
        return new KnowledgeAtomGenerationResult(sourceFile.getId(), 0, 0, false);
    }

    private KnowledgeSourceFile requireVisibleConvertedSourceFile(Long sourceFileId, Long currentUserId) {
        if (currentUserId == null) {
            throw new RuntimeException("未登录：缺少用户身份");
        }
        KnowledgeSourceFile sourceFile = sourceFileMapper.selectById(sourceFileId);
        if (sourceFile == null || !canManage(sourceFile, currentUserId)) {
            throw new RuntimeException("无权访问文件");
        }
        if (sourceFile.getMarkdownStorageKey() == null || sourceFile.getMarkdownStorageKey().isBlank()) {
            throw new IllegalArgumentException("文件尚未完成 Markdown 转换");
        }
        return sourceFile;
    }

    private boolean canManage(KnowledgeSourceFile sourceFile, Long currentUserId) {
        if ("PRIVATE".equalsIgnoreCase(sourceFile.getScope())
                && currentUserId.equals(sourceFile.getOwnerUserId())) {
            return true;
        }
        return adminRoleService.isAdmin(currentUserId);
    }
}
