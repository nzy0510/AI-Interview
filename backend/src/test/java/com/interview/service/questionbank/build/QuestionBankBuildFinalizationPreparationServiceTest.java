package com.interview.service.questionbank.build;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.interview.dto.questionbank.KnowledgeAtomPayload;
import com.interview.dto.questionbank.QuestionBankImportRequest;
import com.interview.dto.questionbank.QuestionBankImportResult;
import com.interview.entity.QuestionBankBuild;
import com.interview.entity.QuestionBankBuildCandidate;
import com.interview.mapper.QuestionBankBuildCandidateMapper;
import com.interview.mapper.QuestionBankBuildMapper;
import com.interview.mapper.KnowledgeSourceFileMapper;
import com.interview.service.questionbank.KnowledgeWorkspaceService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class QuestionBankBuildFinalizationPreparationServiceTest {

    @Test
    void shouldPersistDraftAtomIdsAndHumanBatchAcceptanceBeforePublishing() {
        QuestionBankBuildMapper buildMapper = mock(QuestionBankBuildMapper.class);
        QuestionBankBuildCandidateMapper candidateMapper = mock(QuestionBankBuildCandidateMapper.class);
        KnowledgeSourceFileMapper sourceFileMapper = mock(KnowledgeSourceFileMapper.class);
        QuestionBankBuildResponseAssembler assembler = mock(QuestionBankBuildResponseAssembler.class);
        KnowledgeWorkspaceService workspace = mock(KnowledgeWorkspaceService.class);
        QuestionBankBuildFinalizationPreparationService service = new QuestionBankBuildFinalizationPreparationService(
                buildMapper, candidateMapper, sourceFileMapper, assembler, workspace);
        QuestionBankBuild build = new QuestionBankBuild();
        build.setId(99L);
        build.setOwnerUserId(7L);
        build.setKnowledgeBaseId(10L);
        build.setCategoriesJson("[\"java\"]");
        build.setFinalizationStatus("NOT_STARTED");
        when(buildMapper.selectById(99L)).thenReturn(build);
        QuestionBankBuildCandidate candidate = candidate();
        when(candidateMapper.selectList(any(QueryWrapper.class))).thenReturn(List.of(candidate));
        when(sourceFileMapper.selectCount(any(QueryWrapper.class))).thenReturn(1L);
        KnowledgeAtomPayload payload = new KnowledgeAtomPayload();
        payload.setId("generated-1");
        payload.setSubject("JVM");
        when(assembler.toPayload(candidate)).thenReturn(payload);
        when(assembler.firstCategory(build)).thenReturn("java");
        when(workspace.importPackage(eq(7L), eq(10L), any(QuestionBankImportRequest.class)))
                .thenReturn(QuestionBankImportResult.builder()
                        .batchId("question-bank-build-99-final")
                        .received(1)
                        .imported(1)
                        .failed(0)
                        .importedAtomIds(List.of("kb10-generated-1"))
                        .build());

        List<String> atomIds = service.prepare(99L, 7L, 10L, List.of(101L));

        assertThat(atomIds).containsExactly("kb10-generated-1");
        assertThat(candidate.getReviewStatus()).isEqualTo("ACCEPTED");
        assertThat(candidate.getReviewReason()).isEqualTo("批次终审接受");
        assertThat(build.getFinalizationStatus()).isEqualTo("READY_TO_PUBLISH");
        assertThat(build.getFinalAtomIdsJson()).contains("kb10-generated-1");
        verify(candidateMapper).updateById(candidate);
    }

    @Test
    void shouldReusePersistedDraftAtomIdsWithoutImportingAgainOnRetry() {
        QuestionBankBuildMapper buildMapper = mock(QuestionBankBuildMapper.class);
        QuestionBankBuildCandidateMapper candidateMapper = mock(QuestionBankBuildCandidateMapper.class);
        QuestionBankBuildResponseAssembler assembler = mock(QuestionBankBuildResponseAssembler.class);
        KnowledgeWorkspaceService workspace = mock(KnowledgeWorkspaceService.class);
        QuestionBankBuildFinalizationPreparationService service = new QuestionBankBuildFinalizationPreparationService(
                buildMapper, candidateMapper, mock(KnowledgeSourceFileMapper.class), assembler, workspace);
        QuestionBankBuild build = new QuestionBankBuild();
        build.setId(99L);
        build.setOwnerUserId(7L);
        build.setKnowledgeBaseId(10L);
        build.setFinalAtomIdsJson("[\"kb10-generated-1\"]");
        when(buildMapper.selectById(99L)).thenReturn(build);

        List<String> atomIds = service.prepare(99L, 7L, 10L, List.of(101L));

        assertThat(atomIds).containsExactly("kb10-generated-1");
        verifyNoInteractions(candidateMapper, assembler, workspace);
    }

    @Test
    void shouldNeverReverseAHumanRejectionDuringPreparation() {
        QuestionBankBuildMapper buildMapper = mock(QuestionBankBuildMapper.class);
        QuestionBankBuildCandidateMapper candidateMapper = mock(QuestionBankBuildCandidateMapper.class);
        QuestionBankBuildFinalizationPreparationService service = new QuestionBankBuildFinalizationPreparationService(
                buildMapper, candidateMapper, mock(KnowledgeSourceFileMapper.class),
                mock(QuestionBankBuildResponseAssembler.class), mock(KnowledgeWorkspaceService.class));
        QuestionBankBuild build = new QuestionBankBuild();
        build.setId(99L); build.setOwnerUserId(7L); build.setKnowledgeBaseId(10L);
        when(buildMapper.selectById(99L)).thenReturn(build);
        QuestionBankBuildCandidate rejected = candidate();
        rejected.setReviewStatus("REJECTED");
        when(candidateMapper.selectList(any(QueryWrapper.class))).thenReturn(List.of(rejected));

        org.assertj.core.api.Assertions.assertThatThrownBy(
                        () -> service.prepare(99L, 7L, 10L, List.of(101L)))
                .hasMessageContaining("人工拒绝");
        verify(candidateMapper, org.mockito.Mockito.never()).updateById(any());
    }

    private QuestionBankBuildCandidate candidate() {
        QuestionBankBuildCandidate candidate = new QuestionBankBuildCandidate();
        candidate.setId(101L);
        candidate.setBuildId(99L);
        candidate.setOwnerUserId(7L);
        candidate.setStableAtomId("generated-1");
        candidate.setSubject("JVM");
        candidate.setCategory("java");
        candidate.setDifficulty("mid");
        candidate.setPrinciples("双亲委派");
        candidate.setFollowUpPathsJson("[\"深入\",\"引导\"]");
        candidate.setSourceRef("notes.md#chunk-0");
        candidate.setSourceFileId(301L);
        candidate.setSourceEvidenceJson("[{\"quote\":\"双亲委派\"}]");
        candidate.setMachineReviewStatus("AUTO_PASS");
        candidate.setReviewStatus("PENDING");
        return candidate;
    }
}
