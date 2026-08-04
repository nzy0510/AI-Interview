package com.interview.service.questionbank;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.interview.config.QuestionBankAccessProperties;
import com.interview.entity.KnowledgeAtom;
import com.interview.entity.KnowledgeAtomVersion;
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
        requireMutationAccess(currentUserId);
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
        requireMutationAccess(currentUserId);
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
                || currentUserId.equals(atom.getOwnerUserId())
                || currentUserId.equals(atom.getPublishedBy());
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
}
