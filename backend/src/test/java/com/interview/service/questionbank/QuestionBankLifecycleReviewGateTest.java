package com.interview.service.questionbank;

import com.interview.entity.KnowledgeAtom;
import com.interview.mapper.KnowledgeAtomImportBatchMapper;
import com.interview.mapper.KnowledgeAtomMapper;
import com.interview.mapper.KnowledgeAtomVersionMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class QuestionBankLifecycleReviewGateTest {

    @Mock
    private KnowledgeAtomMapper atomMapper;
    @Mock
    private KnowledgeAtomVersionMapper versionMapper;
    @Mock
    private KnowledgeAtomImportBatchMapper batchMapper;
    @Mock
    private QdrantVectorService qdrantVectorService;

    @Test
    void selectedPublishSkipsUnreviewedRejectedAndNonDraftAtoms() {
        KnowledgeAtom pass = draft("pass", "PASS");
        KnowledgeAtom pending = draft("pending", "NEEDS_REVIEW");
        KnowledgeAtom rejected = draft("rejected", "REJECT");
        KnowledgeAtom missingSource = draft("missing-source", "PASS");
        missingSource.setSourceRef(" ");
        KnowledgeAtom published = draft("published", "PASS");
        published.setStatus("PUBLISHED");
        when(atomMapper.selectList(any())).thenReturn(List.of(pass, pending, rejected, missingSource, published));
        when(qdrantVectorService.upsert(pass)).thenReturn(true);

        QuestionBankLifecycleService service = new QuestionBankLifecycleService(
                atomMapper, versionMapper, batchMapper,
                new QuestionBankVectorSyncService(atomMapper, versionMapper, batchMapper, qdrantVectorService));

        Map<String, Integer> result = service.publishAtoms(List.of(
                "pass", "pending", "rejected", "missing-source", "published"));

        assertThat(result).containsEntry("matched", 5)
                .containsEntry("published", 1)
                .containsEntry("synced", 1)
                .containsEntry("skipped", 4);
        verify(atomMapper, org.mockito.Mockito.atLeastOnce()).updateById(pass);
        verify(atomMapper, never()).updateById(pending);
        verify(atomMapper, never()).updateById(rejected);
        verify(atomMapper, never()).updateById(missingSource);
        verify(atomMapper, never()).updateById(published);
        verify(qdrantVectorService).upsert(pass);
    }

    @Test
    void publishAllDraftsSkipsReviewStatusesAndReturnsSkippedCount() {
        KnowledgeAtom pass = draft("pass", "PASS");
        KnowledgeAtom pending = draft("pending", "NEEDS_REVIEW");
        KnowledgeAtom rejected = draft("rejected", "REJECT");
        KnowledgeAtom missingEvidence = draft("missing-evidence", "PASS");
        missingEvidence.setSourceEvidenceJson("[]");
        when(atomMapper.selectList(any())).thenReturn(List.of(pass, pending, rejected, missingEvidence));
        when(qdrantVectorService.upsert(pass)).thenReturn(true);

        QuestionBankLifecycleService service = new QuestionBankLifecycleService(
                atomMapper, versionMapper, batchMapper,
                new QuestionBankVectorSyncService(atomMapper, versionMapper, batchMapper, qdrantVectorService));

        Map<String, Integer> result = service.publishAllDrafts(
                new QuestionBankImportScope("PRIVATE", 7L, 20L, 30L, 7L, false));

        assertThat(result).containsEntry("matched", 4)
                .containsEntry("published", 1)
                .containsEntry("synced", 1)
                .containsEntry("skipped", 3);
        verify(atomMapper, org.mockito.Mockito.atLeastOnce()).updateById(pass);
        verify(atomMapper, never()).updateById(pending);
        verify(atomMapper, never()).updateById(rejected);
        verify(atomMapper, never()).updateById(missingEvidence);
    }

    @Test
    void publishingDraftRevisionArchivesItsPreviousPublishedBaseAfterVectorSync() {
        KnowledgeAtom revision = draft("base-draft-abc", "PASS");
        revision.setScope("PUBLIC");
        revision.setOwnerUserId(null);
        KnowledgeAtom base = draft("base", "PASS");
        base.setScope("PUBLIC");
        base.setOwnerUserId(null);
        base.setStatus("PUBLISHED");
        base.setPublicationStatus("PUBLISHED");
        when(atomMapper.selectList(any())).thenReturn(List.of(revision), List.of(base));
        when(qdrantVectorService.upsert(revision)).thenReturn(true);
        when(qdrantVectorService.delete("base")).thenReturn(true);

        QuestionBankLifecycleService service = new QuestionBankLifecycleService(
                atomMapper, versionMapper, batchMapper,
                new QuestionBankVectorSyncService(atomMapper, versionMapper, batchMapper, qdrantVectorService));

        Map<String, Integer> result = service.publishAtoms(
                List.of("base-draft-abc"),
                new QuestionBankImportScope("PUBLIC", null, 20L, 30L, 8L, true));

        assertThat(result).containsEntry("published", 1).containsEntry("synced", 1);
        assertThat(base.getStatus()).isEqualTo("ARCHIVED");
        assertThat(base.getVectorStatus()).isEqualTo("DELETED");
        verify(qdrantVectorService).delete("base");
    }

    @Test
    void retryingFailedRevisionVectorSyncAlsoArchivesItsPreviousPublishedBase() {
        KnowledgeAtom revision = draft("base-draft-retry", "PASS");
        revision.setScope("PUBLIC");
        revision.setOwnerUserId(null);
        KnowledgeAtom base = draft("base", "PASS");
        base.setScope("PUBLIC");
        base.setOwnerUserId(null);
        base.setStatus("PUBLISHED");
        base.setPublicationStatus("PUBLISHED");
        when(atomMapper.selectList(any())).thenReturn(List.of(revision), List.of(revision), List.of(base));
        when(qdrantVectorService.upsert(revision)).thenReturn(false, true);
        when(qdrantVectorService.delete("base")).thenReturn(true);

        QuestionBankVectorSyncService vectorSyncService = new QuestionBankVectorSyncService(
                atomMapper, versionMapper, batchMapper, qdrantVectorService);
        QuestionBankLifecycleService lifecycleService = new QuestionBankLifecycleService(
                atomMapper, versionMapper, batchMapper, vectorSyncService);
        QuestionBankImportScope scope = new QuestionBankImportScope("PUBLIC", null, 20L, 30L, 8L, true);

        Map<String, Integer> publishResult = lifecycleService.publishAtoms(List.of("base-draft-retry"), scope);
        assertThat(publishResult).containsEntry("failed", 1);
        assertThat(base.getStatus()).isEqualTo("PUBLISHED");

        Map<String, Integer> reindexResult = vectorSyncService.reindexAtoms(List.of("base-draft-retry"), scope);

        assertThat(reindexResult).containsEntry("synced", 1);
        assertThat(base.getStatus()).isEqualTo("ARCHIVED");
        assertThat(base.getVectorStatus()).isEqualTo("DELETED");
        verify(qdrantVectorService).delete("base");
    }

    @Test
    void ensuringFinalizedAtomsIndexedMustNotRepeatAlreadySyncedUpserts() {
        KnowledgeAtom synced = draft("already-synced", "PASS");
        synced.setStatus("PUBLISHED");
        synced.setVectorStatus("SYNCED");
        when(atomMapper.selectList(any())).thenReturn(List.of(synced));
        QuestionBankVectorSyncService vectorSyncService = new QuestionBankVectorSyncService(
                atomMapper, versionMapper, batchMapper, qdrantVectorService);

        Map<String, Integer> result = vectorSyncService.ensureAtomsIndexed(
                List.of("already-synced"),
                new QuestionBankImportScope("PRIVATE", 7L, 20L, 30L, 7L, false));

        assertThat(result).containsEntry("matched", 1).containsEntry("synced", 1).containsEntry("failed", 0);
        verify(qdrantVectorService, never()).upsert(any());
    }

    private KnowledgeAtom draft(String atomId, String reviewStatus) {
        KnowledgeAtom atom = new KnowledgeAtom();
        atom.setId((long) atomId.hashCode());
        atom.setAtomId(atomId);
        atom.setSubject(atomId);
        atom.setCategory("java");
        atom.setDifficulty("mid");
        atom.setPrinciples("answer");
        atom.setPitfalls("pitfall");
        atom.setFollowUpPathsJson("[\"deep\",\"guide\"]");
        atom.setStatus("DRAFT");
        atom.setPublicationStatus("DRAFT");
        atom.setReviewStatus(reviewStatus);
        atom.setScope("PRIVATE");
        atom.setOwnerUserId(7L);
        atom.setPositionId(20L);
        atom.setKnowledgeBaseId(30L);
        atom.setSourceRef("source.pdf#page=1");
        atom.setSourceEvidenceJson("[{\"quote\":\"source quote\"}]");
        return atom;
    }
}
