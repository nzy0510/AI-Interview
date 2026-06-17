package com.interview.service.questionbank;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.interview.entity.KnowledgeAtom;
import com.interview.mapper.KnowledgeAtomImportBatchMapper;
import com.interview.mapper.KnowledgeAtomMapper;
import com.interview.mapper.KnowledgeAtomVersionMapper;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

class QuestionBankLifecycleService extends QuestionBankSupport {

    private final QuestionBankVectorSyncService vectorSyncService;

    QuestionBankLifecycleService(KnowledgeAtomMapper atomMapper,
                                 KnowledgeAtomVersionMapper versionMapper,
                                 KnowledgeAtomImportBatchMapper batchMapper,
                                 QuestionBankVectorSyncService vectorSyncService) {
        super(atomMapper, versionMapper, batchMapper);
        this.vectorSyncService = vectorSyncService;
    }

    Map<String, Integer> archiveAtoms(List<String> atomIds) {
        return archiveAtoms(atomIds, null);
    }

    Map<String, Integer> archiveAtoms(List<String> atomIds, QuestionBankImportScope scope) {
        List<String> ids = cleanAtomIds(atomIds);
        if (ids.isEmpty()) return resultMap("matched", 0, "archived", 0, "deleted", 0, "skipped", 0);

        List<KnowledgeAtom> atoms = atomMapper.selectList(applyScope(new QueryWrapper<KnowledgeAtom>().in("atom_id", ids), scope));
        int archived = 0;
        int deleted = 0;
        int skipped = 0;
        for (KnowledgeAtom atom : atoms) {
            if (QuestionBankService.STATUS_ARCHIVED.equalsIgnoreCase(atom.getStatus())) {
                skipped++;
                continue;
            }
            atom.setStatus(QuestionBankService.STATUS_ARCHIVED);
            atom.setVectorStatus("PENDING_DELETE");
            atomMapper.updateById(atom);
            recordVersion(atom, "archive:admin");
            boolean vectorDeleted = vectorSyncService.deleteVector(atom);
            atom.setVectorStatus(vectorDeleted ? "DELETED" : "DELETE_FAILED");
            atomMapper.updateById(atom);
            if (vectorDeleted) deleted++;
            archived++;
        }
        return resultMap("matched", atoms.size(), "archived", archived, "deleted", deleted, "skipped", skipped);
    }

    Map<String, Integer> publishAtoms(List<String> atomIds) {
        return publishAtoms(atomIds, null);
    }

    Map<String, Integer> publishAtoms(List<String> atomIds, QuestionBankImportScope scope) {
        List<String> ids = cleanAtomIds(atomIds);
        if (ids.isEmpty()) return resultMap("matched", 0, "published", 0, "synced", 0, "failed", 0);
        List<KnowledgeAtom> atoms = atomMapper.selectList(applyScope(new QueryWrapper<KnowledgeAtom>().in("atom_id", ids), scope));
        int published = 0;
        int synced = 0;
        int failed = 0;
        for (KnowledgeAtom atom : atoms) {
            atom.setStatus(QuestionBankService.STATUS_PUBLISHED);
            atom.setPublicationStatus(QuestionBankService.STATUS_PUBLISHED);
            atom.setPublishedBy(scope == null ? null : scope.currentUserId());
            atom.setPublishedAt(LocalDateTime.now());
            atom.setVectorStatus("PENDING");
            atomMapper.updateById(atom);
            recordVersion(atom, scope == null ? "publish:admin" : "publish:workspace");
            published++;
            if (vectorSyncService.syncAtom(atom)) {
                synced++;
            } else {
                failed++;
            }
        }
        return resultMap("matched", atoms.size(), "published", published, "synced", synced, "failed", failed);
    }

    Map<String, Integer> publishAllDrafts(QuestionBankImportScope scope) {
        List<KnowledgeAtom> draftAtoms = atomMapper.selectList(
                applyScope(new QueryWrapper<KnowledgeAtom>(), scope).eq("status", QuestionBankService.STATUS_DRAFT));
        if (draftAtoms.isEmpty()) {
            return resultMap("matched", 0, "published", 0, "synced", 0, "failed", 0, "skipped", 0);
        }
        int published = 0;
        int synced = 0;
        int failed = 0;
        int skipped = 0;
        for (KnowledgeAtom atom : draftAtoms) {
            if (QuestionBankService.STATUS_ARCHIVED.equalsIgnoreCase(atom.getStatus())) {
                skipped++;
                continue;
            }
            atom.setStatus(QuestionBankService.STATUS_PUBLISHED);
            atom.setPublicationStatus(QuestionBankService.STATUS_PUBLISHED);
            atom.setPublishedBy(scope.currentUserId());
            atom.setPublishedAt(LocalDateTime.now());
            atom.setVectorStatus("PENDING");
            atomMapper.updateById(atom);
            recordVersion(atom, "publish:workspace-all-drafts");
            published++;
            if (vectorSyncService.syncAtom(atom)) {
                synced++;
            } else {
                failed++;
            }
        }
        return resultMap("matched", draftAtoms.size(), "published", published,
                "synced", synced, "failed", failed, "skipped", skipped);
    }

    Map<String, Integer> archiveAll(QuestionBankImportScope scope) {
        List<KnowledgeAtom> activeAtoms = atomMapper.selectList(
                applyScope(new QueryWrapper<KnowledgeAtom>(), scope)
                        .ne("status", QuestionBankService.STATUS_ARCHIVED));
        if (activeAtoms.isEmpty()) {
            return resultMap("matched", 0, "archived", 0, "deleted", 0, "skipped", 0);
        }
        List<String> atomIds = activeAtoms.stream()
                .map(KnowledgeAtom::getAtomId)
                .collect(Collectors.toList());
        return archiveAtoms(atomIds, scope);
    }

    Map<String, Integer> archiveBatch(String batchId) {
        return archiveAtoms(batchAtomIds(batchId, true));
    }
}
