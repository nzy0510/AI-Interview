package com.interview.service.questionbank.build;

import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.interview.dto.questionbank.KnowledgeAtomPayload;
import com.interview.dto.questionbank.QuestionBankImportRequest;
import com.interview.dto.questionbank.QuestionBankImportResult;
import com.interview.entity.QuestionBankBuild;
import com.interview.entity.QuestionBankBuildCandidate;
import com.interview.entity.KnowledgeSourceFile;
import com.interview.mapper.KnowledgeSourceFileMapper;
import com.interview.mapper.QuestionBankBuildCandidateMapper;
import com.interview.mapper.QuestionBankBuildMapper;
import com.interview.service.questionbank.KnowledgeWorkspaceService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Service
public class QuestionBankBuildFinalizationPreparationService {
    private final QuestionBankBuildMapper buildMapper;
    private final QuestionBankBuildCandidateMapper candidateMapper;
    private final KnowledgeSourceFileMapper sourceFileMapper;
    private final QuestionBankBuildResponseAssembler assembler;
    private final KnowledgeWorkspaceService workspaceService;

    public QuestionBankBuildFinalizationPreparationService(QuestionBankBuildMapper buildMapper,
                                                           QuestionBankBuildCandidateMapper candidateMapper,
                                                           KnowledgeSourceFileMapper sourceFileMapper,
                                                           QuestionBankBuildResponseAssembler assembler,
                                                           KnowledgeWorkspaceService workspaceService) {
        this.buildMapper = buildMapper;
        this.candidateMapper = candidateMapper;
        this.sourceFileMapper = sourceFileMapper;
        this.assembler = assembler;
        this.workspaceService = workspaceService;
    }

    @Transactional
    public List<String> prepare(Long buildId,
                                Long userId,
                                Long knowledgeBaseId,
                                List<Long> candidateIds) {
        QuestionBankBuild build = buildMapper.selectById(buildId);
        if (build == null || !userId.equals(build.getOwnerUserId())
                || !knowledgeBaseId.equals(build.getKnowledgeBaseId())) {
            throw new IllegalArgumentException("终审构建不存在或无权访问");
        }
        if (build.getFinalAtomIdsJson() != null && !build.getFinalAtomIdsJson().isBlank()) {
            return JSON.parseArray(build.getFinalAtomIdsJson(), String.class);
        }
        List<QuestionBankBuildCandidate> selected = candidateMapper.selectList(
                new QueryWrapper<QuestionBankBuildCandidate>()
                        .eq("build_id", buildId)
                        .eq("owner_user_id", userId)
                        .in("id", candidateIds)
                        .orderByAsc("chunk_index", "id"));
        if (selected.size() != candidateIds.size()) {
            throw new IllegalArgumentException("终审候选不存在或不属于当前构建");
        }
        if (selected.stream().anyMatch(candidate -> "REJECTED".equalsIgnoreCase(candidate.getReviewStatus()))) {
            throw new IllegalArgumentException("终审不能包含人工拒绝的候选");
        }
        validateSourceFiles(selected, buildId, userId, knowledgeBaseId);
        for (QuestionBankBuildCandidate candidate : selected) {
            if (!"ACCEPTED".equalsIgnoreCase(candidate.getReviewStatus())) {
                if (!"PENDING".equalsIgnoreCase(candidate.getReviewStatus())
                        || !"AUTO_PASS".equalsIgnoreCase(candidate.getMachineReviewStatus())) {
                    throw new IllegalArgumentException("终审候选尚未通过机器监督或人工接受");
                }
                candidate.setReviewStatus("ACCEPTED");
                candidate.setReviewReason("批次终审接受");
                candidateMapper.updateById(candidate);
            }
        }
        List<KnowledgeAtomPayload> atoms = selected.stream().map(assembler::toPayload).toList();
        QuestionBankImportRequest request = new QuestionBankImportRequest();
        request.setBatchId("question-bank-build-" + buildId + "-final");
        request.setMode("DRAFT");
        request.setTargetCategory(assembler.firstCategory(build));
        request.setSourceRef("question-bank-build:" + buildId + ":final-review");
        request.setAtoms(atoms);
        request.setReviewReport(Map.of(
                "humanFinalReview", true,
                "selectedCandidateCount", selected.size(),
                "reviewedBy", userId
        ));
        build.setFinalizationStatus("IMPORTING");
        buildMapper.updateById(build);
        QuestionBankImportResult imported = workspaceService.importPackage(userId, knowledgeBaseId, request);
        if (imported == null || imported.getFailed() > 0 || imported.getImported() != selected.size()
                || imported.getImportedAtomIds() == null
                || imported.getImportedAtomIds().size() != selected.size()) {
            throw new IllegalStateException("终审草稿导入不完整，已回滚本次操作");
        }
        build.setFinalImportBatchId(imported.getBatchId());
        build.setFinalAtomIdsJson(JSON.toJSONString(imported.getImportedAtomIds()));
        build.setFinalizationStatus("READY_TO_PUBLISH");
        build.setStage("PUBLISHING");
        build.setProgress(75);
        build.setFinalizedBy(userId);
        build.setFinalizedAt(LocalDateTime.now());
        buildMapper.updateById(build);
        return imported.getImportedAtomIds();
    }

    private void validateSourceFiles(List<QuestionBankBuildCandidate> candidates,
                                     Long buildId,
                                     Long userId,
                                     Long knowledgeBaseId) {
        List<Long> sourceFileIds = candidates.stream()
                .map(QuestionBankBuildCandidate::getSourceFileId)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .toList();
        if (sourceFileIds.isEmpty() || candidates.stream().anyMatch(item -> item.getSourceFileId() == null)) {
            throw new IllegalStateException("终审候选缺少来源文件关联");
        }
        long matched = sourceFileMapper.selectCount(new QueryWrapper<KnowledgeSourceFile>()
                .in("id", sourceFileIds)
                .eq("build_id", buildId)
                .eq("owner_user_id", userId)
                .eq("knowledge_base_id", knowledgeBaseId));
        if (matched != sourceFileIds.size()) {
            throw new IllegalStateException("终审候选来源文件不属于当前构建");
        }
    }
}
