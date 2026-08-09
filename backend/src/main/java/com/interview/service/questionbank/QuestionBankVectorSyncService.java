package com.interview.service.questionbank;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.interview.entity.KnowledgeAtom;
import com.interview.mapper.KnowledgeAtomImportBatchMapper;
import com.interview.mapper.KnowledgeAtomMapper;
import com.interview.mapper.KnowledgeAtomVersionMapper;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

class QuestionBankVectorSyncService extends QuestionBankSupport {

    private final QdrantVectorService qdrantVectorService;

    QuestionBankVectorSyncService(KnowledgeAtomMapper atomMapper,
                                  KnowledgeAtomVersionMapper versionMapper,
                                  KnowledgeAtomImportBatchMapper batchMapper,
                                  QdrantVectorService qdrantVectorService) {
        super(atomMapper, versionMapper, batchMapper);
        this.qdrantVectorService = qdrantVectorService;
    }

    Map<String, Integer> reindexAtoms(List<String> atomIds) {
        return reindexAtoms(atomIds, null);
    }

    Map<String, Integer> reindexAtoms(List<String> atomIds, QuestionBankImportScope scope) {
        List<String> ids = cleanAtomIds(atomIds);
        if (ids.isEmpty()) return resultMap("matched", 0, "synced", 0, "failed", 0, "skipped", 0);
        List<KnowledgeAtom> atoms = atomMapper.selectList(applyScope(new QueryWrapper<KnowledgeAtom>().in("atom_id", ids), scope));
        int synced = 0;
        int failed = 0;
        int skipped = 0;
        for (KnowledgeAtom atom : atoms) {
            if (!QuestionBankService.STATUS_PUBLISHED.equalsIgnoreCase(atom.getStatus())) {
                skipped++;
                continue;
            }
            if (syncReindexedAtom(atom)) {
                synced++;
            } else {
                failed++;
            }
        }
        return resultMap("matched", atoms.size(), "synced", synced, "failed", failed, "skipped", skipped);
    }

    Map<String, Integer> ensureAtomsIndexed(List<String> atomIds, QuestionBankImportScope scope) {
        List<String> ids = cleanAtomIds(atomIds);
        if (ids.isEmpty()) return resultMap("matched", 0, "synced", 0, "failed", 0, "skipped", 0);
        List<KnowledgeAtom> atoms = atomMapper.selectList(
                applyScope(new QueryWrapper<KnowledgeAtom>().in("atom_id", ids), scope));
        int synced = 0;
        int failed = 0;
        int skipped = 0;
        for (KnowledgeAtom atom : atoms) {
            if (!QuestionBankService.STATUS_PUBLISHED.equalsIgnoreCase(atom.getStatus())) {
                skipped++;
                continue;
            }
            if ("SYNCED".equalsIgnoreCase(atom.getVectorStatus())) {
                synced++;
                continue;
            }
            if (syncReindexedAtom(atom)) synced++;
            else failed++;
        }
        return resultMap("matched", atoms.size(), "synced", synced, "failed", failed, "skipped", skipped);
    }

    Map<String, Integer> reindexUnsyncedPublishedAtomResult() {
        List<KnowledgeAtom> publishedAtoms = atomMapper.selectList(new QueryWrapper<KnowledgeAtom>()
                .eq("status", QuestionBankService.STATUS_PUBLISHED)
                .ne("vector_status", "SYNCED"));
        List<KnowledgeAtom> archivedAtoms = atomMapper.selectList(new QueryWrapper<KnowledgeAtom>()
                .eq("status", QuestionBankService.STATUS_ARCHIVED)
                .in("vector_status", List.of("PENDING_DELETE", "DELETE_FAILED")));
        int synced = 0;
        int failed = 0;
        for (KnowledgeAtom atom : publishedAtoms) {
            if (syncReindexedAtom(atom)) synced++;
            else failed++;
        }
        int deleted = 0;
        for (KnowledgeAtom atom : archivedAtoms) {
            boolean ok = deleteVector(atom);
            atom.setVectorStatus(ok ? "DELETED" : "DELETE_FAILED");
            atomMapper.updateById(atom);
            if (ok) deleted++;
            else failed++;
        }
        return resultMap("matched", publishedAtoms.size() + archivedAtoms.size(),
                "synced", synced, "deleted", deleted, "failed", failed);
    }

    Map<String, Integer> reindexAllPublishedAtomResult() {
        List<KnowledgeAtom> atoms = atomMapper.selectList(new QueryWrapper<KnowledgeAtom>()
                .eq("status", QuestionBankService.STATUS_PUBLISHED));
        int synced = 0;
        int failed = 0;
        for (KnowledgeAtom atom : atoms) {
            if (syncReindexedAtom(atom)) synced++;
            else failed++;
        }
        return resultMap("matched", atoms.size(), "synced", synced, "failed", failed);
    }

    int reindexPublishedAtoms() {
        List<KnowledgeAtom> atoms = atomMapper.selectList(new QueryWrapper<KnowledgeAtom>()
                .eq("status", QuestionBankService.STATUS_PUBLISHED));
        int synced = 0;
        for (KnowledgeAtom atom : atoms) {
            if (syncReindexedAtom(atom)) synced++;
        }
        return synced;
    }

    int reindexUnsyncedPublishedAtoms() {
        Map<String, Integer> result = reindexUnsyncedPublishedAtomResult();
        return result.getOrDefault("synced", 0) + result.getOrDefault("deleted", 0);
    }

    boolean syncAtom(KnowledgeAtom atom) {
        boolean ok = qdrantVectorService.upsert(atom);
        atom.setVectorStatus(ok ? "SYNCED" : "FAILED");
        atom.setLastIndexedAt(ok ? LocalDateTime.now() : atom.getLastIndexedAt());
        atomMapper.updateById(atom);
        return ok;
    }

    private boolean syncReindexedAtom(KnowledgeAtom atom) {
        boolean synced = syncAtom(atom);
        if (synced) archivePreviousDraftBase(atom);
        return synced;
    }

    private void archivePreviousDraftBase(KnowledgeAtom atom) {
        String atomId = atom == null ? null : atom.getAtomId();
        if (atomId == null || atomId.isBlank()) return;
        int draftMarker = atomId.indexOf("-draft-");
        if (draftMarker <= 0) return;
        String baseAtomId = atomId.substring(0, draftMarker);
        QueryWrapper<KnowledgeAtom> query = new QueryWrapper<KnowledgeAtom>()
                .eq("atom_id", baseAtomId)
                .eq("status", QuestionBankService.STATUS_PUBLISHED)
                .eq(atom.getScope() != null, "scope", atom.getScope())
                .eq(atom.getPositionId() != null, "position_id", atom.getPositionId())
                .eq(atom.getKnowledgeBaseId() != null, "knowledge_base_id", atom.getKnowledgeBaseId());
        if (atom.getOwnerUserId() == null) query.isNull("owner_user_id");
        else query.eq("owner_user_id", atom.getOwnerUserId());
        for (KnowledgeAtom base : atomMapper.selectList(query)) {
            base.setStatus(QuestionBankService.STATUS_ARCHIVED);
            base.setVectorStatus("PENDING_DELETE");
            atomMapper.updateById(base);
            recordVersion(base, "archive:replaced-revision");
            boolean deleted = deleteVector(base);
            base.setVectorStatus(deleted ? "DELETED" : "DELETE_FAILED");
            atomMapper.updateById(base);
        }
    }

    boolean deleteVector(KnowledgeAtom atom) {
        return qdrantVectorService.delete(atom.getAtomId());
    }
}
