package com.interview.service.questionbank;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.interview.entity.AppJob;
import com.interview.entity.KnowledgeAtom;
import com.interview.entity.KnowledgeAtomVersion;
import com.interview.entity.KnowledgeSourceFile;
import com.interview.mapper.KnowledgeAtomMapper;
import com.interview.mapper.KnowledgeAtomVersionMapper;
import com.interview.mapper.KnowledgeSourceFileMapper;
import com.interview.service.AdminRoleService;
import com.interview.service.UserLlmConfigService;
import com.interview.service.UserLlmRuntimeConfig;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class KnowledgeAtomWorkflowService {

    public static final String JOB_TYPE_GENERATE_ATOMS = "GENERATE_ATOMS";
    private static final int MAX_DRAFTS_PER_IMPORT = 100;
    private static final Pattern SENSITIVE_PATTERN = Pattern.compile(
            "(?i)(Authorization\\s*[:=]\\s*\\S+|Bearer\\s+\\S+|api_key\\s*[:=]\\s*\\S+|sk-[A-Za-z0-9_-]+)"
    );

    private final KnowledgeSourceFileMapper sourceFileMapper;
    private final KnowledgeAtomMapper atomMapper;
    private final KnowledgeAtomVersionMapper versionMapper;
    private final FileStorageService fileStorageService;
    private final UserLlmConfigService userLlmConfigService;
    private final AdminRoleService adminRoleService;
    private final KnowledgeAtomAiClient aiClient;
    private final QuestionBankService questionBankService;

    public KnowledgeAtomWorkflowService(KnowledgeSourceFileMapper sourceFileMapper,
                                        KnowledgeAtomMapper atomMapper,
                                        KnowledgeAtomVersionMapper versionMapper,
                                        FileStorageService fileStorageService,
                                        UserLlmConfigService userLlmConfigService,
                                        AdminRoleService adminRoleService,
                                        KnowledgeAtomAiClient aiClient,
                                        QuestionBankService questionBankService) {
        this.sourceFileMapper = sourceFileMapper;
        this.atomMapper = atomMapper;
        this.versionMapper = versionMapper;
        this.fileStorageService = fileStorageService;
        this.userLlmConfigService = userLlmConfigService;
        this.adminRoleService = adminRoleService;
        this.aiClient = aiClient;
        this.questionBankService = questionBankService;
    }

    public List<KnowledgeAtomResponse> listAtomsForSourceFile(Long sourceFileId, Long currentUserId) {
        requireVisibleSourceFile(sourceFileId, currentUserId);
        return atomMapper.selectList(new QueryWrapper<KnowledgeAtom>()
                        .eq("source_file_id", sourceFileId)
                        .orderByDesc("id"))
                .stream()
                .filter(atom -> isVisible(atom, currentUserId))
                .map(KnowledgeAtomResponse::from)
                .collect(Collectors.toList());
    }

    @Transactional
    public KnowledgeAtomResponse createManualAtom(Long sourceFileId, Long currentUserId, KnowledgeAtomPatch patch) {
        KnowledgeSourceFile sourceFile = requireManageableSourceFile(sourceFileId, currentUserId);
        KnowledgeAtom atom = new KnowledgeAtom();
        atom.setAtomId("manual-" + sourceFile.getId() + "-" + UUID.randomUUID());
        atom.setSubject("未命名考点");
        atom.setCategory("通用");
        atom.setDifficulty("MEDIUM");
        atom.setTagsJson("[]");
        atom.setPrinciples("待补充");
        atom.setFollowUpPathsJson("[]");
        atom.setStatus("DRAFT");
        atom.setVectorStatus("SKIPPED");
        atom.setScope(sourceFile.getScope());
        atom.setOwnerUserId(sourceFile.getOwnerUserId());
        atom.setPositionId(sourceFile.getPositionId());
        atom.setKnowledgeBaseId(sourceFile.getKnowledgeBaseId());
        atom.setSourceFileId(sourceFile.getId());
        atom.setSourceRef(sourceFile.getOriginalFilename());
        atom.setCurrentVersionNo(1);
        atom.setReviewStatus("PASS");
        atom.setReviewReason("人工创建");
        atom.setPublicationStatus("DRAFT");
        applyPatch(atom, patch);
        atomMapper.insert(atom);
        recordVersion(atom, "manual:create");
        return KnowledgeAtomResponse.from(atom);
    }

    @Transactional
    public KnowledgeAtomGenerationResult generateAtomsForJob(AppJob job) {
        if (job == null || job.getSourceFileId() == null) {
            throw new RuntimeException("原子生成作业缺少源文件");
        }
        KnowledgeSourceFile sourceFile = sourceFileMapper.selectById(job.getSourceFileId());
        if (sourceFile == null) {
            throw new RuntimeException("源文件不存在");
        }
        try {
            if (sourceFile.getMarkdownStorageKey() == null || sourceFile.getMarkdownStorageKey().isBlank()) {
                throw new IllegalArgumentException("文件尚未完成 Markdown 转换");
            }
            Long runtimeUserId = job.getCreatedBy() != null ? job.getCreatedBy() : sourceFile.getCreatedBy();
            UserLlmRuntimeConfig runtimeConfig = userLlmConfigService.requireActiveRuntimeConfig(runtimeUserId);
            String markdown = fileStorageService.readText(sourceFile.getMarkdownStorageKey());
            KnowledgeAtomDraftBundle bundle = aiClient.generateReviewedAtoms(runtimeConfig, markdown);
            List<KnowledgeAtomDraft> drafts = bundle.atoms() == null ? List.of() : bundle.atoms();
            int imported = 0;
            for (KnowledgeAtomDraft draft : drafts.stream().limit(MAX_DRAFTS_PER_IMPORT).toList()) {
                KnowledgeAtom atom = toDraftAtom(draft, sourceFile);
                atomMapper.insert(atom);
                recordVersion(atom, "generate:" + sourceFile.getId());
                imported++;
            }
            sourceFile.setStatus("ATOMS_GENERATED");
            sourceFile.setErrorMessage(null);
            sourceFileMapper.updateById(sourceFile);
            return new KnowledgeAtomGenerationResult(sourceFile.getId(), drafts.size(), imported,
                    bundle.atomLimitReached() || drafts.size() > MAX_DRAFTS_PER_IMPORT);
        } catch (Exception e) {
            String sanitized = sanitize(e.getMessage());
            sourceFile.setStatus("FAILED");
            sourceFile.setErrorMessage(sanitized);
            sourceFileMapper.updateById(sourceFile);
            throw new RuntimeException("知识原子生成失败：" + sanitized, e);
        }
    }

    @Transactional
    public KnowledgeAtomResponse acceptSuggestedPatch(Long atomId, Long currentUserId) {
        KnowledgeAtom atom = requireManageableAtom(atomId, currentUserId);
        if (atom.getSuggestedPatchJson() == null || atom.getSuggestedPatchJson().isBlank()) {
            throw new IllegalArgumentException("当前原子没有可应用的建议补丁");
        }
        KnowledgeAtomPatch patch = parsePatch(atom.getSuggestedPatchJson());
        applyPatch(atom, patch);
        atom.setReviewStatus("PASS");
        atom.setReviewReason("已应用模型建议补丁");
        atom.setSuggestedPatchJson(null);
        atomMapper.updateById(atom);
        recordVersion(atom, "review:accept-patch");
        return KnowledgeAtomResponse.from(atom);
    }

    @Transactional
    public KnowledgeAtomResponse updateAtom(Long atomId, Long currentUserId, KnowledgeAtomPatch patch) {
        KnowledgeAtom atom = requireManageableAtom(atomId, currentUserId);
        if ("PUBLISHED".equalsIgnoreCase(atom.getPublicationStatus())
                || "PUBLISHED".equalsIgnoreCase(atom.getStatus())) {
            KnowledgeAtom draft = cloneAsDraftRevision(atom, currentUserId);
            applyPatch(draft, patch);
            atomMapper.insert(draft);
            recordVersion(draft, "edit:draft-revision");
            return KnowledgeAtomResponse.from(draft);
        }
        applyPatch(atom, patch);
        atom.setReviewStatus("PASS");
        atom.setReviewReason("人工修订后通过");
        atom.setSuggestedPatchJson(null);
        atomMapper.updateById(atom);
        recordVersion(atom, "edit:draft");
        return KnowledgeAtomResponse.from(atom);
    }

    @Transactional
    public KnowledgeAtomResponse publishAtom(Long atomId, Long currentUserId) {
        KnowledgeAtom atom = requireManageableAtom(atomId, currentUserId);
        String reviewStatus = normalizeReviewStatus(atom.getReviewStatus());
        if ("REJECT".equals(reviewStatus)) {
            throw new IllegalArgumentException("REJECT 原子不可发布");
        }
        if ("NEEDS_REVIEW".equals(reviewStatus)) {
            throw new IllegalArgumentException("NEEDS_REVIEW 原子需要先应用补丁或人工修订");
        }
        publishDraftAtom(atom, currentUserId, "publish:user");
        return KnowledgeAtomResponse.from(atom);
    }

    @Transactional
    public KnowledgeAtomBulkPublishResult publishAtomsForSourceFile(Long sourceFileId, Long currentUserId) {
        KnowledgeSourceFile sourceFile = requireManageableSourceFile(sourceFileId, currentUserId);
        List<KnowledgeAtom> atoms = atomMapper.selectList(new QueryWrapper<KnowledgeAtom>()
                .eq("source_file_id", sourceFile.getId()));
        int published = 0;
        int synced = 0;
        int failed = 0;
        int skipped = 0;
        for (KnowledgeAtom atom : atoms) {
            if (!isPublishableDraft(atom)) {
                skipped++;
                continue;
            }
            boolean syncOk = publishDraftAtom(atom, currentUserId, "publish:source-file");
            published++;
            if (syncOk) {
                synced++;
            } else {
                failed++;
            }
        }
        return new KnowledgeAtomBulkPublishResult(sourceFile.getId(), atoms.size(), published, synced, failed, skipped);
    }

    private KnowledgeSourceFile requireVisibleConvertedSourceFile(Long sourceFileId, Long currentUserId) {
        KnowledgeSourceFile sourceFile = requireVisibleSourceFile(sourceFileId, currentUserId);
        if (sourceFile.getMarkdownStorageKey() == null || sourceFile.getMarkdownStorageKey().isBlank()) {
            throw new IllegalArgumentException("文件尚未完成 Markdown 转换");
        }
        return sourceFile;
    }

    private KnowledgeSourceFile requireVisibleSourceFile(Long sourceFileId, Long currentUserId) {
        if (currentUserId == null) {
            throw new RuntimeException("未登录：缺少用户身份");
        }
        KnowledgeSourceFile sourceFile = sourceFileMapper.selectById(sourceFileId);
        if (sourceFile == null || !isVisible(sourceFile, currentUserId)) {
            throw new RuntimeException("无权访问文件");
        }
        return sourceFile;
    }

    private KnowledgeSourceFile requireManageableSourceFile(Long sourceFileId, Long currentUserId) {
        KnowledgeSourceFile sourceFile = requireVisibleSourceFile(sourceFileId, currentUserId);
        if (!canManage(sourceFile, currentUserId)) {
            throw new RuntimeException("无权访问文件");
        }
        return sourceFile;
    }

    private boolean isVisible(KnowledgeSourceFile sourceFile, Long currentUserId) {
        return "PUBLIC".equalsIgnoreCase(sourceFile.getScope())
                || currentUserId.equals(sourceFile.getOwnerUserId())
                || currentUserId.equals(sourceFile.getCreatedBy());
    }

    private KnowledgeAtom requireVisibleAtom(Long atomId, Long currentUserId) {
        if (currentUserId == null) {
            throw new RuntimeException("未登录：缺少用户身份");
        }
        KnowledgeAtom atom = atomMapper.selectById(atomId);
        if (atom == null || !isVisible(atom, currentUserId)) {
            throw new RuntimeException("无权访问知识原子");
        }
        return atom;
    }

    private KnowledgeAtom requireManageableAtom(Long atomId, Long currentUserId) {
        KnowledgeAtom atom = requireVisibleAtom(atomId, currentUserId);
        if (!canManage(atom, currentUserId)) {
            throw new RuntimeException("无权访问知识原子");
        }
        return atom;
    }

    private boolean isVisible(KnowledgeAtom atom, Long currentUserId) {
        return "PUBLIC".equalsIgnoreCase(atom.getScope())
                || currentUserId.equals(atom.getOwnerUserId())
                || currentUserId.equals(atom.getPublishedBy());
    }

    private boolean canManage(KnowledgeSourceFile sourceFile, Long currentUserId) {
        if ("PRIVATE".equalsIgnoreCase(sourceFile.getScope())
                && currentUserId.equals(sourceFile.getOwnerUserId())) {
            return true;
        }
        return adminRoleService.isAdmin(currentUserId);
    }

    private boolean canManage(KnowledgeAtom atom, Long currentUserId) {
        if ("PRIVATE".equalsIgnoreCase(atom.getScope())
                && currentUserId.equals(atom.getOwnerUserId())) {
            return true;
        }
        return adminRoleService.isAdmin(currentUserId);
    }

    private void archivePreviousDraftBase(String atomId) {
        if (atomId == null || atomId.isBlank()) return;
        int draftMarker = atomId.indexOf("-draft-");
        if (draftMarker <= 0) return;
        questionBankService.archiveAtoms(List.of(atomId.substring(0, draftMarker)));
    }

    private boolean isPublishableDraft(KnowledgeAtom atom) {
        return "PASS".equals(normalizeReviewStatus(atom.getReviewStatus()))
                && !"PUBLISHED".equalsIgnoreCase(atom.getPublicationStatus())
                && !"PUBLISHED".equalsIgnoreCase(atom.getStatus());
    }

    private boolean publishDraftAtom(KnowledgeAtom atom, Long currentUserId, String reason) {
        atom.setCurrentVersionNo((atom.getCurrentVersionNo() == null ? 1 : atom.getCurrentVersionNo()) + 1);
        atom.setStatus("PUBLISHED");
        atom.setPublicationStatus("PUBLISHED");
        atom.setVectorStatus("PENDING");
        atom.setVectorErrorMessage(null);
        atom.setPublishedBy(currentUserId);
        atom.setPublishedAt(LocalDateTime.now());
        atomMapper.updateById(atom);
        recordVersion(atom, reason);
        boolean synced = questionBankService.syncAtom(atom);
        if (synced) {
            archivePreviousDraftBase(atom.getAtomId());
        }
        return synced;
    }

    private KnowledgeAtom toDraftAtom(KnowledgeAtomDraft draft, KnowledgeSourceFile sourceFile) {
        KnowledgeAtom atom = new KnowledgeAtom();
        atom.setAtomId("atom-" + sourceFile.getId() + "-" + UUID.randomUUID());
        atom.setSubject(nonBlank(draft.subject(), "未命名考点"));
        atom.setCategory(nonBlank(draft.category(), "通用"));
        atom.setDifficulty(nonBlank(draft.difficulty(), "MEDIUM").toUpperCase(Locale.ROOT));
        atom.setTagsJson(JSON.toJSONString(draft.tags() == null ? List.of() : draft.tags()));
        atom.setPrinciples(nonBlank(draft.principles(), "待补充"));
        atom.setPitfalls(draft.pitfalls());
        atom.setFollowUpPathsJson(JSON.toJSONString(draft.followUpPaths() == null ? List.of() : draft.followUpPaths()));
        atom.setStatus("DRAFT");
        atom.setSourceRef(sourceFile.getOriginalFilename());
        atom.setChecksum(checksum(atom));
        atom.setVectorStatus("SKIPPED");
        atom.setScope(sourceFile.getScope());
        atom.setOwnerUserId(sourceFile.getOwnerUserId());
        atom.setPositionId(sourceFile.getPositionId());
        atom.setKnowledgeBaseId(sourceFile.getKnowledgeBaseId());
        atom.setSourceFileId(sourceFile.getId());
        atom.setCurrentVersionNo(1);
        KnowledgeAtomReviewResult review = draft.review();
        atom.setReviewStatus(normalizeReviewStatus(review == null ? null : review.status()));
        atom.setReviewReason(review == null ? "模型未返回二审结果" : review.reason());
        atom.setReviewConfidence(review == null ? null : review.confidence());
        atom.setSuggestedPatchJson(review == null || review.suggestedPatch() == null ? null : JSON.toJSONString(review.suggestedPatch()));
        atom.setPublicationStatus("DRAFT");
        atom.setReviewedAt(LocalDateTime.now());
        atom.setReviewedBy(sourceFile.getCreatedBy());
        return atom;
    }

    private void recordVersion(KnowledgeAtom atom, String reason) {
        KnowledgeAtomVersion version = new KnowledgeAtomVersion();
        version.setAtomId(atom.getAtomId());
        version.setVersionNo(atom.getCurrentVersionNo() == null ? 1 : atom.getCurrentVersionNo());
        version.setSnapshotJson(JSON.toJSONString(atom));
        version.setChangeReason(reason);
        versionMapper.insert(version);
    }

    private KnowledgeAtom cloneAsDraftRevision(KnowledgeAtom atom, Long currentUserId) {
        KnowledgeAtom draft = new KnowledgeAtom();
        draft.setAtomId(atom.getAtomId() + "-draft-" + UUID.randomUUID());
        draft.setSubject(atom.getSubject());
        draft.setCategory(atom.getCategory());
        draft.setDifficulty(atom.getDifficulty());
        draft.setTagsJson(atom.getTagsJson());
        draft.setPrinciples(atom.getPrinciples());
        draft.setPitfalls(atom.getPitfalls());
        draft.setFollowUpPathsJson(atom.getFollowUpPathsJson());
        draft.setStatus("DRAFT");
        draft.setSourceRef(atom.getSourceRef());
        draft.setChecksum(atom.getChecksum());
        draft.setVectorStatus("SKIPPED");
        draft.setScope(atom.getScope());
        draft.setOwnerUserId(atom.getOwnerUserId());
        draft.setPositionId(atom.getPositionId());
        draft.setKnowledgeBaseId(atom.getKnowledgeBaseId());
        draft.setSourceFileId(atom.getSourceFileId());
        draft.setCurrentVersionNo((atom.getCurrentVersionNo() == null ? 1 : atom.getCurrentVersionNo()) + 1);
        draft.setReviewStatus("PASS");
        draft.setReviewReason("人工修订后通过");
        draft.setReviewConfidence(atom.getReviewConfidence());
        draft.setPublicationStatus("DRAFT");
        draft.setReviewedBy(currentUserId);
        draft.setReviewedAt(LocalDateTime.now());
        return draft;
    }

    private KnowledgeAtomPatch parsePatch(String patchJson) {
        JSONObject patch = JSON.parseObject(patchJson);
        return new KnowledgeAtomPatch(
                patch.getString("subject"),
                patch.getString("category"),
                patch.getString("difficulty"),
                patch.getList("tags", String.class),
                patch.getString("principles"),
                patch.getString("pitfalls"),
                patch.getList("followUpPaths", String.class)
        );
    }

    private void applyPatch(KnowledgeAtom atom, KnowledgeAtomPatch patch) {
        if (patch == null) return;
        if (patch.subject() != null && !patch.subject().isBlank()) atom.setSubject(patch.subject().trim());
        if (patch.category() != null && !patch.category().isBlank()) atom.setCategory(patch.category().trim());
        if (patch.difficulty() != null && !patch.difficulty().isBlank()) atom.setDifficulty(patch.difficulty().trim());
        if (patch.tags() != null) atom.setTagsJson(JSON.toJSONString(patch.tags()));
        if (patch.principles() != null && !patch.principles().isBlank()) atom.setPrinciples(patch.principles().trim());
        if (patch.pitfalls() != null) atom.setPitfalls(patch.pitfalls());
        if (patch.followUpPaths() != null) atom.setFollowUpPathsJson(JSON.toJSONString(patch.followUpPaths()));
        atom.setChecksum(checksum(atom));
    }

    private String normalizeReviewStatus(String status) {
        if (status == null) return "NEEDS_REVIEW";
        String normalized = status.trim().toUpperCase(Locale.ROOT);
        if (List.of("PASS", "NEEDS_REVIEW", "REJECT").contains(normalized)) return normalized;
        return "NEEDS_REVIEW";
    }

    private String checksum(KnowledgeAtom atom) {
        String raw = String.join("|",
                atom.getSubject(), atom.getCategory(), atom.getDifficulty(), atom.getTagsJson(),
                atom.getPrinciples(), String.valueOf(atom.getPitfalls()), atom.getFollowUpPathsJson());
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(raw.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            return Integer.toHexString(raw.hashCode());
        }
    }

    private String sanitize(String message) {
        if (message == null || message.isBlank()) {
            return "知识原子生成失败";
        }
        return SENSITIVE_PATTERN.matcher(message).replaceAll("[REDACTED]");
    }

    private String nonBlank(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }
}
