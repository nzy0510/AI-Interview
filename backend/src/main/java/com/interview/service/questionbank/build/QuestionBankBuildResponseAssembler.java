package com.interview.service.questionbank.build;

import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.interview.dto.questionbank.KnowledgeAtomPayload;
import com.interview.dto.questionbank.build.QuestionBankBuildCandidateResponse;
import com.interview.dto.questionbank.build.QuestionBankBuildResponse;
import com.interview.entity.AppJob;
import com.interview.entity.KnowledgeSourceFile;
import com.interview.entity.QuestionBankBuild;
import com.interview.entity.QuestionBankBuildCandidate;
import com.interview.mapper.AppJobMapper;
import com.interview.mapper.KnowledgeSourceFileMapper;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class QuestionBankBuildResponseAssembler {
    private final KnowledgeSourceFileMapper sourceFileMapper;
    private final AppJobMapper appJobMapper;

    public QuestionBankBuildResponseAssembler(KnowledgeSourceFileMapper sourceFileMapper, AppJobMapper appJobMapper) {
        this.sourceFileMapper = sourceFileMapper;
        this.appJobMapper = appJobMapper;
    }

    public QuestionBankBuildResponse toResponse(QuestionBankBuild build, Long jobId) {
        QuestionBankBuildResponse response = new QuestionBankBuildResponse();
        response.setBuildId(build.getId()); response.setJobId(jobId); response.setStatus(build.getStatus()); response.setStage(build.getStage()); response.setProgress(build.getProgress());
        response.setOwnerUserId(build.getOwnerUserId()); response.setPositionId(build.getPositionId()); response.setKnowledgeBaseId(build.getKnowledgeBaseId());
        response.setCategories(parseStringList(build.getCategoriesJson()));
        List<KnowledgeSourceFile> files = sourceFileMapper.selectList(new QueryWrapper<KnowledgeSourceFile>().eq("build_id", build.getId()).orderByAsc("id"));
        response.setSourceFiles(files.stream().map(this::toSourceFile).toList());
        response.setFileCount(files.size()); response.setChunkCount(build.getChunkCount()); response.setEstimatedCallCount(estimatedCallCount(build));
        response.setCompletedChunkCount(build.getCompletedChunkCount()); response.setCandidateCount(build.getCandidateCount()); response.setAcceptedCount(build.getAcceptedCount()); response.setRejectedCount(build.getRejectedCount());
        response.setAutoPassCount(build.getAutoPassCount()); response.setNeedsHumanCount(build.getNeedsHumanCount()); response.setAutoRejectCount(build.getAutoRejectCount()); response.setReviewRevision(build.getReviewRevision()); response.setFinalizationStatus(build.getFinalizationStatus()); response.setFinalAtomIds(parseStringList(build.getFinalAtomIdsJson()));
        response.setConfigId(build.getLlmConfigId()); response.setProvider(build.getLlmProvider()); response.setModel(build.getLlmModel()); response.setPromptVersion(build.getPromptVersion());
        response.setErrorMessage(build.getErrorMessage()); response.setFinalizationResult(parseMap(build.getFinalizationResultJson())); response.setFinalizedBy(build.getFinalizedBy()); response.setFinalizedAt(build.getFinalizedAt()); response.setCreateTime(build.getCreateTime()); response.setUpdateTime(build.getUpdateTime());
        return response;
    }

    public QuestionBankBuildCandidateResponse toCandidateResponse(QuestionBankBuildCandidate candidate) {
        QuestionBankBuildCandidateResponse response = new QuestionBankBuildCandidateResponse();
        response.setCandidateId(candidate.getId()); response.setStableAtomId(candidate.getStableAtomId()); response.setBuildId(candidate.getBuildId()); response.setChunkIndex(candidate.getChunkIndex()); response.setSourceFileId(candidate.getSourceFileId()); response.setSourceRef(candidate.getSourceRef());
        response.setSourceEvidence(parseEvidence(candidate.getSourceEvidenceJson())); response.setSubject(candidate.getSubject()); response.setCategory(candidate.getCategory()); response.setDifficulty(candidate.getDifficulty()); response.setTags(parseStringList(candidate.getTagsJson())); response.setPrinciples(candidate.getPrinciples()); response.setPitfalls(candidate.getPitfalls()); response.setFollowUpPaths(parseStringList(candidate.getFollowUpPathsJson()));
        response.setSelfCheck(parseMap(candidate.getSelfCheckJson())); response.setDuplicateHint(candidate.getDuplicateHint()); response.setMachineReviewStatus(candidate.getMachineReviewStatus()); response.setMachineReviewScore(candidate.getMachineReviewScore()); response.setMachineReviewIssues(parseStringList(candidate.getMachineReviewIssuesJson())); response.setMachineSuggestedPatch(parseMap(candidate.getMachineSuggestedPatchJson())); response.setMachineReviewPromptVersion(candidate.getMachineReviewPromptVersion()); response.setMachineReviewAttempts(candidate.getMachineReviewAttempts()); response.setMachineReviewedAt(candidate.getMachineReviewedAt()); response.setReviewStatus(candidate.getReviewStatus()); response.setReviewReason(candidate.getReviewReason()); response.setUpdatedAt(candidate.getUpdatedAt());
        return response;
    }

    public KnowledgeAtomPayload toPayload(QuestionBankBuildCandidate candidate) {
        KnowledgeAtomPayload payload = new KnowledgeAtomPayload(); payload.setId(candidate.getStableAtomId()); payload.setSubject(candidate.getSubject()); payload.setCategory(candidate.getCategory()); payload.setDifficulty(candidate.getDifficulty()); payload.setTags(parseStringList(candidate.getTagsJson())); payload.setSourceRef(candidate.getSourceRef()); payload.setSourceFileId(candidate.getSourceFileId()); payload.setSourceEvidence(parseCoreEvidence(candidate.getSourceEvidenceJson()));
        KnowledgeAtomPayload.Content content = new KnowledgeAtomPayload.Content(); content.setPrinciples(candidate.getPrinciples()); content.setPitfalls(candidate.getPitfalls()); content.setFollowUpPaths(parseStringList(candidate.getFollowUpPathsJson())); payload.setContent(content); return payload;
    }

    public Long jobIdFor(Long buildId) {
        AppJob job = appJobMapper.selectOne(new QueryWrapper<AppJob>().eq("build_id", buildId).orderByDesc("id").last("LIMIT 1"));
        return job == null ? null : job.getId();
    }

    public String firstCategory(QuestionBankBuild build) {
        List<String> categories = parseStringList(build.getCategoriesJson()); return categories.isEmpty() ? "通用" : categories.get(0);
    }

    private int estimatedCallCount(QuestionBankBuild build) {
        int chunks = build.getChunkCount() == null ? 0 : build.getChunkCount();
        int supervisionCalls = build.getCandidateCount() == null || build.getCandidateCount() == 0
                ? chunks : build.getCandidateCount();
        return chunks + supervisionCalls;
    }

    private QuestionBankBuildResponse.SourceFileResponse toSourceFile(KnowledgeSourceFile file) {
        QuestionBankBuildResponse.SourceFileResponse item = new QuestionBankBuildResponse.SourceFileResponse(); item.setSourceFileId(file.getId()); item.setOriginalFilename(file.getOriginalFilename()); item.setSize(file.getFileSize()); item.setContentType(file.getContentType()); item.setStatus(file.getStatus()); return item;
    }

    private List<String> parseStringList(String json) {
        if (json == null || json.isBlank()) return new ArrayList<>();
        try { return JSON.parseArray(json, String.class); } catch (Exception e) { return new ArrayList<>(); }
    }

    private Map<String, Object> parseMap(String json) {
        if (json == null || json.isBlank()) return null;
        try { return JSON.parseObject(json, Map.class); } catch (Exception e) { return null; }
    }

    private List<KnowledgeAtomPayload.SourceEvidence> parseCoreEvidence(String json) {
        List<KnowledgeAtomPayload.SourceEvidence> result = new ArrayList<>(); if (json == null || json.isBlank()) return result;
        try {
            for (Map value : JSON.parseArray(json, Map.class)) { KnowledgeAtomPayload.SourceEvidence item = new KnowledgeAtomPayload.SourceEvidence(); item.setQuote(value.get("quote") == null ? null : String.valueOf(value.get("quote"))); item.setPageOrSection(value.get("pageOrSection") == null ? null : String.valueOf(value.get("pageOrSection"))); result.add(item); }
        } catch (Exception ignored) { }
        return result;
    }

    private List<QuestionBankBuildCandidateResponse.SourceEvidence> parseEvidence(String json) {
        List<QuestionBankBuildCandidateResponse.SourceEvidence> result = new ArrayList<>(); if (json == null || json.isBlank()) return result;
        try {
            for (Map value : JSON.parseArray(json, Map.class)) { QuestionBankBuildCandidateResponse.SourceEvidence item = new QuestionBankBuildCandidateResponse.SourceEvidence(); item.setQuote(value.get("quote") == null ? null : String.valueOf(value.get("quote"))); item.setPageOrSection(value.get("pageOrSection") == null ? null : String.valueOf(value.get("pageOrSection"))); result.add(item); }
        } catch (Exception ignored) { }
        return result;
    }
}
