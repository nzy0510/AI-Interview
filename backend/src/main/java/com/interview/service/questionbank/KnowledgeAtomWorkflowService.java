package com.interview.service.questionbank;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.interview.config.QuestionBankAccessProperties;
import com.interview.entity.KnowledgeAtom;
import com.interview.mapper.KnowledgeAtomMapper;
import com.interview.mapper.KnowledgeAtomVersionMapper;
import com.interview.service.AdminRoleService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
public class KnowledgeAtomWorkflowService {

    private final KnowledgeAtomMapper atomMapper;
    private final KnowledgeAtomVersionMapper versionMapper;
    private final AdminRoleService adminRoleService;
    private final QuestionBankService questionBankService;
    private final QuestionBankAccessProperties accessProperties;

    public KnowledgeAtomWorkflowService(KnowledgeAtomMapper atomMapper,
                                        KnowledgeAtomVersionMapper versionMapper,
                                        AdminRoleService adminRoleService,
                                        QuestionBankService questionBankService,
                                        QuestionBankAccessProperties accessProperties) {
        this.atomMapper = atomMapper;
        this.versionMapper = versionMapper;
        this.adminRoleService = adminRoleService;
        this.questionBankService = questionBankService;
        this.accessProperties = accessProperties;
    }

    @Transactional
    public KnowledgeAtomResponse acceptSuggestedPatch(Long atomId, Long currentUserId) {
        requireMutationAccess(currentUserId);
        KnowledgeAtom atom = requireManageableAtom(atomId, currentUserId);
        if (atom.getSuggestedPatchJson() == null || atom.getSuggestedPatchJson().isBlank()) {
            throw new IllegalArgumentException("当前原子没有可应用的建议补丁");
        }
        KnowledgeAtomPatch patch = parsePatch(atom.getSuggestedPatchJson());
        boolean published = "PUBLISHED".equalsIgnoreCase(atom.getPublicationStatus())
                || "PUBLISHED".equalsIgnoreCase(atom.getStatus());
        KnowledgeAtom target = published ? cloneAsDraftRevision(atom, currentUserId) : atom;
        String checksumBeforePatch = checksum(target);
        applyPatch(target, patch);
        validateReviewableAtom(target);
        requireActualReviewChange(atom, target, checksumBeforePatch);
        target.setReviewStatus("PASS");
        target.setReviewReason("已应用模型建议补丁");
        target.setReviewedBy(currentUserId);
        target.setReviewedAt(LocalDateTime.now());
        target.setSuggestedPatchJson(null);
        if (published) atomMapper.insert(target);
        else QuestionBankSupport.updateAtomContent(atomMapper, target);
        recordVersion(target, "review:accept-patch");
        return KnowledgeAtomResponse.from(target);
    }

    @Transactional
    public KnowledgeAtomResponse updateAtom(Long atomId, Long currentUserId, KnowledgeAtomPatch patch) {
        requireMutationAccess(currentUserId);
        KnowledgeAtom atom = requireManageableAtom(atomId, currentUserId);
        if ("PUBLISHED".equalsIgnoreCase(atom.getPublicationStatus())
                || "PUBLISHED".equalsIgnoreCase(atom.getStatus())) {
            KnowledgeAtom draft = cloneAsDraftRevision(atom, currentUserId);
            String checksumBeforePatch = checksum(draft);
            applyPatch(draft, patch);
            validateReviewableAtom(draft);
            requireActualReviewChange(atom, draft, checksumBeforePatch);
            atomMapper.insert(draft);
            recordVersion(draft, "edit:draft-revision");
            return KnowledgeAtomResponse.from(draft);
        }
        String checksumBeforePatch = checksum(atom);
        applyPatch(atom, patch);
        validateReviewableAtom(atom);
        requireActualReviewChange(atom, atom, checksumBeforePatch);
        atom.setReviewStatus("PASS");
        atom.setReviewReason("人工修订后通过");
        atom.setReviewedBy(currentUserId);
        atom.setReviewedAt(LocalDateTime.now());
        atom.setSuggestedPatchJson(null);
        QuestionBankSupport.updateAtomContent(atomMapper, atom);
        recordVersion(atom, "edit:draft");
        return KnowledgeAtomResponse.from(atom);
    }

    @Transactional
    public KnowledgeAtomResponse publishAtom(Long atomId, Long currentUserId) {
        requireMutationAccess(currentUserId);
        KnowledgeAtom atom = requireManageableAtom(atomId, currentUserId);
        if (!"DRAFT".equalsIgnoreCase(atom.getStatus())) {
            throw new IllegalArgumentException("只有 DRAFT 原子可以发布");
        }
        String reviewStatus = normalizeReviewStatus(atom.getReviewStatus());
        if ("REJECT".equals(reviewStatus)) {
            throw new IllegalArgumentException("REJECT 原子不可发布");
        }
        if ("NEEDS_REVIEW".equals(reviewStatus)) {
            throw new IllegalArgumentException("NEEDS_REVIEW 原子需要先应用补丁或人工修订");
        }
        validateReviewableAtom(atom);
        publishDraftAtom(atom, currentUserId, "publish:user");
        return KnowledgeAtomResponse.from(atom);
    }

    /** Return a single atom only when the caller can manage its scope. */
    public KnowledgeAtomResponse getAtom(Long atomId, Long currentUserId) {
        requireMutationAccess(currentUserId);
        return KnowledgeAtomResponse.from(requireManageableAtom(atomId, currentUserId));
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

    private void requireMutationAccess(Long currentUserId) {
        if (currentUserId == null) {
            throw new RuntimeException("未登录：缺少用户身份");
        }
        if (!accessProperties.isUserMaintenanceEnabled() && !adminRoleService.isAdmin(currentUserId)) {
            throw new RuntimeException("无权访问题库维护");
        }
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
                || ("PRIVATE".equalsIgnoreCase(atom.getScope())
                && currentUserId.equals(atom.getOwnerUserId()));
    }

    private boolean canManage(KnowledgeAtom atom, Long currentUserId) {
        if ("PRIVATE".equalsIgnoreCase(atom.getScope())
                && currentUserId.equals(atom.getOwnerUserId())) {
            return accessProperties.isUserMaintenanceEnabled();
        }
        return "PUBLIC".equalsIgnoreCase(atom.getScope())
                && adminRoleService.isAdmin(currentUserId);
    }

    private void archivePreviousDraftBase(String atomId) {
        if (atomId == null || atomId.isBlank()) return;
        int draftMarker = atomId.indexOf("-draft-");
        if (draftMarker <= 0) return;
        questionBankService.archiveAtoms(List.of(atomId.substring(0, draftMarker)));
    }

    private boolean publishDraftAtom(KnowledgeAtom atom, Long currentUserId, String reason) {
        atom.setStatus("PUBLISHED");
        atom.setPublicationStatus("PUBLISHED");
        atom.setVectorStatus("PENDING");
        atom.setVectorErrorMessage(null);
        atom.setPublishedBy(currentUserId);
        atom.setPublishedAt(LocalDateTime.now());
        QuestionBankSupport.updateAtomContent(atomMapper, atom);
        recordVersion(atom, reason);
        boolean synced = questionBankService.syncAtom(atom);
        if (synced) {
            archivePreviousDraftBase(atom.getAtomId());
        }
        return synced;
    }

    private void recordVersion(KnowledgeAtom atom, String reason) {
        QuestionBankSupport.recordVersion(atomMapper, versionMapper, atom, reason);
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
        draft.setSourceEvidenceJson(atom.getSourceEvidenceJson());
        draft.setCurrentVersionNo(atom.getCurrentVersionNo());
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

    private void validateReviewableAtom(KnowledgeAtom atom) {
        if (atom.getSubject() == null || atom.getSubject().isBlank()) {
            throw new IllegalArgumentException("考点不能为空");
        }
        if (atom.getCategory() == null || atom.getCategory().isBlank()) {
            throw new IllegalArgumentException("分类不能为空");
        }
        String difficulty = atom.getDifficulty() == null ? "" : atom.getDifficulty().trim().toLowerCase(Locale.ROOT);
        if (!Set.of("junior", "mid", "senior", "principal").contains(difficulty)) {
            throw new IllegalArgumentException("难度只能是 junior、mid、senior 或 principal");
        }
        atom.setDifficulty(difficulty);
        if (atom.getPrinciples() == null || atom.getPrinciples().isBlank()) {
            throw new IllegalArgumentException("核心原理不能为空");
        }
        List<String> followUpPaths;
        try {
            followUpPaths = JSON.parseArray(atom.getFollowUpPathsJson(), String.class);
        } catch (RuntimeException ignored) {
            followUpPaths = List.of();
        }
        long followUpCount = followUpPaths == null ? 0 : followUpPaths.stream()
                .filter(path -> path != null && !path.isBlank())
                .count();
        if (followUpCount < 2) {
            throw new IllegalArgumentException("请至少填写两条追问路径");
        }
        QuestionBankSupport.validateRequiredSourceEvidence(atom);
    }

    private void requireActualReviewChange(KnowledgeAtom source,
                                           KnowledgeAtom patched,
                                           String checksumBeforePatch) {
        if ("NEEDS_REVIEW".equals(normalizeReviewStatus(source.getReviewStatus()))
                && checksumBeforePatch.equals(checksum(patched))) {
            throw new IllegalArgumentException("NEEDS_REVIEW 原子必须提交实际修改后才能通过审核");
        }
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
}
