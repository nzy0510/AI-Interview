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
            if (syncAtom(atom)) {
                synced++;
            } else {
                failed++;
            }
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
            if (syncAtom(atom)) synced++;
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
            if (syncAtom(atom)) synced++;
            else failed++;
        }
        return resultMap("matched", atoms.size(), "synced", synced, "failed", failed);
    }

    int reindexPublishedAtoms() {
        List<KnowledgeAtom> atoms = atomMapper.selectList(new QueryWrapper<KnowledgeAtom>()
                .eq("status", QuestionBankService.STATUS_PUBLISHED));
        int synced = 0;
        for (KnowledgeAtom atom : atoms) {
            if (syncAtom(atom)) synced++;
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

    boolean deleteVector(KnowledgeAtom atom) {
        return qdrantVectorService.delete(atom.getAtomId());
    }
}
