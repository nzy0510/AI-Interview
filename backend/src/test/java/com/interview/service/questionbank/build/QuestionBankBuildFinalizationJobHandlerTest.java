package com.interview.service.questionbank.build;

import com.interview.entity.AppJob;
import com.interview.entity.QuestionBankBuild;
import com.interview.mapper.QuestionBankBuildMapper;
import com.interview.service.AppJobService;
import com.interview.service.questionbank.KnowledgeWorkspaceService;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class QuestionBankBuildFinalizationJobHandlerTest {

    @Test
    void shouldPreparePublishAndRetryFailedIndexesWithinTheSameFinalReviewJob() {
        QuestionBankBuildMapper buildMapper = mock(QuestionBankBuildMapper.class);
        QuestionBankBuildFinalizationPreparationService preparation = mock(QuestionBankBuildFinalizationPreparationService.class);
        KnowledgeWorkspaceService workspace = mock(KnowledgeWorkspaceService.class);
        AppJobService appJobService = mock(AppJobService.class);
        QuestionBankBuildFinalizationJobHandler handler = new QuestionBankBuildFinalizationJobHandler(
                buildMapper, preparation, workspace, appJobService);
        QuestionBankBuild build = new QuestionBankBuild();
        build.setId(99L);
        build.setScope("PRIVATE");
        build.setOwnerUserId(7L);
        build.setKnowledgeBaseId(10L);
        build.setStatus("PENDING");
        build.setStage("FINALIZING");
        when(buildMapper.selectById(99L)).thenReturn(build);
        when(preparation.prepare(99L, 7L, 10L, List.of(101L, 102L)))
                .thenReturn(List.of("kb10-a", "kb10-b"));
        when(workspace.publishAtoms(any(), any(), any())).thenReturn(Map.of(
                "matched", 2, "published", 2, "synced", 1, "failed", 1, "skipped", 0));
        when(workspace.ensureAtomsIndexed(any(), any(), any())).thenReturn(Map.of(
                "matched", 2, "synced", 2, "failed", 0, "skipped", 0));
        AppJob job = new AppJob();
        job.setId(501L);
        job.setBuildId(99L);
        job.setOwnerUserId(7L);
        job.setKnowledgeBaseId(10L);
        job.setClaimedBy("worker");
        job.setPayloadJson("{\"candidateIds\":[101,102]}");

        handler.handle(job);

        assertThat(build.getStatus()).isEqualTo("COMPLETED");
        assertThat(build.getStage()).isEqualTo("PUBLISHED");
        assertThat(build.getFinalizationStatus()).isEqualTo("COMPLETED");
        assertThat(job.getResultJson()).contains("\"failed\":0");
        verify(workspace).ensureAtomsIndexed(any(), any(), any());
        verify(appJobService).updateRunningJob(501L, "worker", "INDEXING", 90);
    }

    @Test
    void shouldFailClosedWhenAnyFinalReviewAtomRemainsUnpublished() {
        QuestionBankBuildMapper buildMapper = mock(QuestionBankBuildMapper.class);
        QuestionBankBuildFinalizationPreparationService preparation = mock(QuestionBankBuildFinalizationPreparationService.class);
        KnowledgeWorkspaceService workspace = mock(KnowledgeWorkspaceService.class);
        QuestionBankBuildFinalizationJobHandler handler = new QuestionBankBuildFinalizationJobHandler(
                buildMapper, preparation, workspace, mock(AppJobService.class));
        QuestionBankBuild build = new QuestionBankBuild();
        build.setId(99L); build.setScope("PRIVATE"); build.setOwnerUserId(7L); build.setKnowledgeBaseId(10L);
        when(buildMapper.selectById(99L)).thenReturn(build);
        when(preparation.prepare(99L, 7L, 10L, List.of(101L, 102L)))
                .thenReturn(List.of("kb10-a", "kb10-b"));
        when(workspace.publishAtoms(any(), any(), any())).thenReturn(Map.of(
                "matched", 2, "published", 1, "synced", 1, "failed", 0, "skipped", 1));
        when(workspace.ensureAtomsIndexed(any(), any(), any())).thenReturn(Map.of(
                "matched", 2, "synced", 1, "failed", 0, "skipped", 1));
        AppJob job = new AppJob();
        job.setId(501L); job.setBuildId(99L); job.setOwnerUserId(7L); job.setKnowledgeBaseId(10L);
        job.setClaimedBy("worker"); job.setPayloadJson("{\"candidateIds\":[101,102]}");

        assertThatThrownBy(() -> handler.handle(job)).hasMessageContaining("未完成");

        assertThat(build.getStatus()).isEqualTo("FAILED");
        assertThat(build.getStage()).isEqualTo("PUBLISHED_WITH_INDEX_ERRORS");
        assertThat(build.getFinalizationStatus()).isEqualTo("PARTIAL_FAILED");
    }
}
