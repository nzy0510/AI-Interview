package com.interview.service.questionbank.build;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.interview.config.QuestionBankBuildProperties;
import com.interview.entity.AppJob;
import com.interview.entity.KnowledgeSourceFile;
import com.interview.entity.QuestionBankBuild;
import com.interview.entity.QuestionBankBuildCandidate;
import com.interview.mapper.KnowledgeSourceFileMapper;
import com.interview.mapper.QuestionBankBuildCandidateMapper;
import com.interview.mapper.QuestionBankBuildMapper;
import com.interview.service.AppJobService;
import com.interview.service.UserLlmConfigService;
import com.interview.service.UserLlmRuntimeConfig;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class QuestionBankBuildJobHandlerTest {
    @Test
    void stableIdMustNotDependOnModelSubject() {
        assertThat(QuestionBankBuildJobHandler.stableAtomId(11L, "hash", 2, 0))
                .isEqualTo(QuestionBankBuildJobHandler.stableAtomId(11L, "hash", 2, 0))
                .isNotEqualTo(QuestionBankBuildJobHandler.stableAtomId(12L, "hash", 2, 0));
    }

    @Test
    void shouldSkipCompletedChunksOnRetry() throws Exception {
        QuestionBankBuildMapper buildMapper = mock(QuestionBankBuildMapper.class);
        when(buildMapper.update(isNull(), any(UpdateWrapper.class))).thenReturn(1);
        QuestionBankBuildCandidateMapper candidateMapper = mock(QuestionBankBuildCandidateMapper.class);
        KnowledgeSourceFileMapper sourceFileMapper = mock(KnowledgeSourceFileMapper.class);
        QuestionBankBuildFileStorage storage = mock(QuestionBankBuildFileStorage.class);
        QuestionBankBuildProperties properties = new QuestionBankBuildProperties();
        UserLlmConfigService configService = mock(UserLlmConfigService.class);
        QuestionBankBuildLlm llm = mock(QuestionBankBuildLlm.class);
        AppJobService appJobService = mock(AppJobService.class);
        QuestionBankBuildJobHandler handler = new QuestionBankBuildJobHandler(
                buildMapper, candidateMapper, sourceFileMapper, storage, properties, configService, llm, appJobService);

        QuestionBankBuild build = new QuestionBankBuild();
        build.setId(11L); build.setScope("PRIVATE"); build.setOwnerUserId(7L); build.setLlmConfigId(3L);
        build.setCheckpointJson("[0]"); build.setChunkCount(1); build.setProgress(50);
        when(buildMapper.selectById(11L)).thenReturn(build);
        KnowledgeSourceFile source = new KnowledgeSourceFile(); source.setId(21L); source.setOriginalFilename("notes.md"); source.setMarkdownStorageKey("builds/11/text/21.txt");
        when(sourceFileMapper.selectList(any(QueryWrapper.class))).thenReturn(List.of(source));
        when(storage.readText(source.getMarkdownStorageKey())).thenReturn("one chunk");
        when(configService.requireOwnedRuntimeConfig(7L, 3L)).thenReturn(
                new UserLlmRuntimeConfig(3L, 7L, "test", "test", "http://localhost", "mock", "secret", 0.0));

        AppJob job = new AppJob(); job.setId(99L); job.setBuildId(11L); job.setClaimedBy("worker");
        handler.handle(job);

        verify(llm, never()).complete(any(), anyString(), anyString());
        verify(buildMapper, atLeastOnce()).updateById(build);
        org.assertj.core.api.Assertions.assertThat(build.getStatus()).isEqualTo(AppJobService.STATUS_COMPLETED);
    }

    @Test
    void shouldPreserveExistingHumanCandidateAndOnlySelfCheckWhenMissing() throws Exception {
        QuestionBankBuildMapper buildMapper = mock(QuestionBankBuildMapper.class);
        when(buildMapper.update(isNull(), any(UpdateWrapper.class))).thenReturn(1);
        QuestionBankBuildCandidateMapper candidateMapper = mock(QuestionBankBuildCandidateMapper.class);
        KnowledgeSourceFileMapper sourceFileMapper = mock(KnowledgeSourceFileMapper.class);
        QuestionBankBuildFileStorage storage = mock(QuestionBankBuildFileStorage.class);
        QuestionBankBuildProperties properties = new QuestionBankBuildProperties();
        UserLlmConfigService configService = mock(UserLlmConfigService.class);
        QuestionBankBuildLlm llm = mock(QuestionBankBuildLlm.class);
        AppJobService appJobService = mock(AppJobService.class);
        QuestionBankBuildJobHandler handler = new QuestionBankBuildJobHandler(
                buildMapper, candidateMapper, sourceFileMapper, storage, properties, configService, llm, appJobService);

        QuestionBankBuild build = new QuestionBankBuild();
        build.setId(11L); build.setScope("PRIVATE"); build.setOwnerUserId(7L); build.setLlmConfigId(3L);
        build.setCheckpointJson("[]"); build.setChunkCount(1); build.setProgress(0);
        when(buildMapper.selectById(11L)).thenReturn(build);
        KnowledgeSourceFile source = new KnowledgeSourceFile(); source.setId(21L); source.setFileHash("hash"); source.setOriginalFilename("notes.md"); source.setMarkdownStorageKey("builds/11/text/21.txt");
        when(sourceFileMapper.selectList(any(QueryWrapper.class))).thenReturn(List.of(source));
        when(storage.readText(source.getMarkdownStorageKey())).thenReturn("one chunk");
        when(configService.requireOwnedRuntimeConfig(7L, 3L)).thenReturn(
                new UserLlmRuntimeConfig(3L, 7L, "test", "test", "http://localhost", "mock", "secret", 0.0));
        QuestionBankBuildCandidate candidate = new QuestionBankBuildCandidate();
        candidate.setId(41L); candidate.setBuildId(11L); candidate.setChunkIndex(0); candidate.setStableAtomId("generated-existing");
        candidate.setSubject("人工修订后的主题"); candidate.setCategory("java"); candidate.setDifficulty("mid"); candidate.setPrinciples("人工答案");
        candidate.setFollowUpPathsJson("[\"深入\",\"引导\"]"); candidate.setSourceRef("notes.md#chunk-0"); candidate.setSourceEvidenceJson("[{\"quote\":\"evidence\"}]"); candidate.setSelfCheckJson("{\"passed\":true}"); candidate.setReviewStatus("PENDING");
        when(candidateMapper.selectList(any(QueryWrapper.class))).thenReturn(List.of(candidate));
        when(candidateMapper.selectCount(any(QueryWrapper.class))).thenReturn(1L, 0L, 0L);
        AppJob job = new AppJob(); job.setId(99L); job.setBuildId(11L); job.setClaimedBy("worker");

        handler.handle(job);

        verify(llm, never()).complete(any(), anyString(), anyString());
        verify(candidateMapper, never()).insert(any());
        assertThat(candidate.getSubject()).isEqualTo("人工修订后的主题");
    }

    @Test
    void shouldResumeExistingCandidateOnlyForMissingSelfCheck() throws Exception {
        QuestionBankBuildMapper buildMapper = mock(QuestionBankBuildMapper.class);
        when(buildMapper.update(isNull(), any(UpdateWrapper.class))).thenReturn(1);
        QuestionBankBuildCandidateMapper candidateMapper = mock(QuestionBankBuildCandidateMapper.class);
        KnowledgeSourceFileMapper sourceFileMapper = mock(KnowledgeSourceFileMapper.class);
        QuestionBankBuildFileStorage storage = mock(QuestionBankBuildFileStorage.class);
        QuestionBankBuildProperties properties = new QuestionBankBuildProperties();
        UserLlmConfigService configService = mock(UserLlmConfigService.class);
        QuestionBankBuildLlm llm = mock(QuestionBankBuildLlm.class);
        AppJobService appJobService = mock(AppJobService.class);
        QuestionBankBuildJobHandler handler = new QuestionBankBuildJobHandler(
                buildMapper, candidateMapper, sourceFileMapper, storage, properties, configService, llm, appJobService);
        QuestionBankBuild build = new QuestionBankBuild(); build.setId(12L); build.setScope("PRIVATE"); build.setOwnerUserId(7L); build.setLlmConfigId(3L); build.setCheckpointJson("[]");
        when(buildMapper.selectById(12L)).thenReturn(build);
        KnowledgeSourceFile source = new KnowledgeSourceFile(); source.setId(22L); source.setFileHash("hash2"); source.setOriginalFilename("notes.md"); source.setMarkdownStorageKey("text");
        when(sourceFileMapper.selectList(any(QueryWrapper.class))).thenReturn(List.of(source)); when(storage.readText("text")).thenReturn("one chunk");
        when(configService.requireOwnedRuntimeConfig(7L, 3L)).thenReturn(new UserLlmRuntimeConfig(3L, 7L, "test", "test", "http://localhost", "mock", "secret", 0.0));
        QuestionBankBuildCandidate candidate = new QuestionBankBuildCandidate(); candidate.setId(42L); candidate.setBuildId(12L); candidate.setChunkIndex(0); candidate.setSubject("主题"); candidate.setCategory("java"); candidate.setDifficulty("mid"); candidate.setPrinciples("答案"); candidate.setFollowUpPathsJson("[\"深入\",\"引导\"]"); candidate.setSourceRef("notes"); candidate.setSourceEvidenceJson("[{\"quote\":\"evidence\"}]"); candidate.setReviewStatus("PENDING");
        when(candidateMapper.selectList(any(QueryWrapper.class))).thenReturn(List.of(candidate)); when(candidateMapper.selectCount(any(QueryWrapper.class))).thenReturn(1L, 0L, 0L);
        when(llm.complete(any(), contains("自检"), anyString())).thenReturn("{\"passed\":true,\"confidence\":0.9}");
        AppJob job = new AppJob(); job.setId(100L); job.setBuildId(12L); job.setClaimedBy("worker");

        handler.handle(job);

        verify(llm, times(1)).complete(any(), contains("自检"), anyString());
        verify(candidateMapper).updateById(argThat(update ->
                update.getId().equals(candidate.getId())
                        && update.getSubject() == null
                        && update.getSelfCheckJson().contains("passed")));
        verify(candidateMapper, never()).insert(any());
    }

    @Test
    void shouldPersistGeneratedCandidateBeforeSelfCheckAndNotRegenerateOnRetry() throws Exception {
        QuestionBankBuildMapper buildMapper = mock(QuestionBankBuildMapper.class);
        when(buildMapper.update(isNull(), any(UpdateWrapper.class))).thenReturn(1);
        QuestionBankBuildCandidateMapper candidateMapper = mock(QuestionBankBuildCandidateMapper.class);
        KnowledgeSourceFileMapper sourceFileMapper = mock(KnowledgeSourceFileMapper.class);
        QuestionBankBuildFileStorage storage = mock(QuestionBankBuildFileStorage.class);
        UserLlmConfigService configService = mock(UserLlmConfigService.class);
        QuestionBankBuildLlm llm = mock(QuestionBankBuildLlm.class);
        AppJobService appJobService = mock(AppJobService.class);
        QuestionBankBuildJobHandler handler = new QuestionBankBuildJobHandler(
                buildMapper, candidateMapper, sourceFileMapper, storage,
                new QuestionBankBuildProperties(), configService, llm, appJobService);

        QuestionBankBuild build = new QuestionBankBuild();
        build.setId(13L); build.setScope("PRIVATE"); build.setOwnerUserId(7L); build.setLlmConfigId(3L);
        build.setCheckpointJson("[]"); build.setChunkCount(1); build.setProgress(0);
        when(buildMapper.selectById(13L)).thenReturn(build);
        KnowledgeSourceFile source = new KnowledgeSourceFile();
        source.setId(23L); source.setFileHash("hash3"); source.setOriginalFilename("notes.md"); source.setMarkdownStorageKey("text-3");
        when(sourceFileMapper.selectList(any(QueryWrapper.class))).thenReturn(List.of(source));
        when(storage.readText("text-3")).thenReturn("JVM class loading");
        UserLlmRuntimeConfig runtime = new UserLlmRuntimeConfig(
                3L, 7L, "test", "test", "http://localhost", "mock", "secret", 0.0);
        when(configService.requireOwnedRuntimeConfig(7L, 3L)).thenReturn(runtime);

        AtomicReference<QuestionBankBuildCandidate> persisted = new AtomicReference<>();
        when(candidateMapper.selectList(any(QueryWrapper.class))).thenAnswer(invocation ->
                persisted.get() == null ? List.of() : List.of(persisted.get()));
        doAnswer(invocation -> {
            QuestionBankBuildCandidate inserted = invocation.getArgument(0);
            inserted.setId(31L);
            persisted.set(inserted);
            return 1;
        }).when(candidateMapper).insert(any(QuestionBankBuildCandidate.class));
        when(candidateMapper.selectCount(any(QueryWrapper.class))).thenReturn(0L);
        when(llm.complete(any(), contains("题库构建 Agent"), anyString())).thenReturn(
                """
                {"subject":"类加载","category":"jvm","difficulty":"mid","tags":[],
                 "content":{"principles":"双亲委派","pitfalls":"破坏隔离",
                 "follow_up_paths":["深入","引导"]},
                 "sourceEvidence":"证据句 A：JVM class loading"}
                """);
        when(llm.complete(any(), contains("题库质量自检器"), anyString()))
                .thenThrow(new IllegalStateException("temporary self-check failure"))
                .thenReturn("""
                        {"passed":true,"confidence":0.9}
                        """);
        AppJob job = new AppJob(); job.setId(101L); job.setBuildId(13L); job.setClaimedBy("worker");

        assertThatThrownBy(() -> handler.handle(job)).hasMessageContaining("temporary self-check failure");
        assertThat(persisted.get()).isNotNull();
        assertThat(persisted.get().getSelfCheckJson()).contains("PENDING");
        assertThat(persisted.get().getSourceEvidenceJson()).contains("证据句 A：JVM class loading");

        handler.handle(job);

        verify(llm, times(1)).complete(any(), contains("题库构建 Agent"), anyString());
        verify(llm, times(2)).complete(any(), contains("题库质量自检器"), anyString());
        verify(candidateMapper, times(1)).insert(any(QuestionBankBuildCandidate.class));
        verify(candidateMapper).updateById(argThat(update ->
                update.getId().equals(persisted.get().getId())
                        && update.getSubject() == null
                        && update.getSelfCheckJson().contains("passed")));
    }

    @Test
    void shouldFailClosedWithoutAnotherGenerationWhenOnlyPartOfAChunkWasPersisted() throws Exception {
        QuestionBankBuildMapper buildMapper = mock(QuestionBankBuildMapper.class);
        when(buildMapper.update(isNull(), any(UpdateWrapper.class))).thenReturn(1);
        QuestionBankBuildCandidateMapper candidateMapper = mock(QuestionBankBuildCandidateMapper.class);
        KnowledgeSourceFileMapper sourceFileMapper = mock(KnowledgeSourceFileMapper.class);
        QuestionBankBuildFileStorage storage = mock(QuestionBankBuildFileStorage.class);
        UserLlmConfigService configService = mock(UserLlmConfigService.class);
        QuestionBankBuildLlm llm = mock(QuestionBankBuildLlm.class);
        QuestionBankBuildJobHandler handler = new QuestionBankBuildJobHandler(
                buildMapper, candidateMapper, sourceFileMapper, storage,
                new QuestionBankBuildProperties(), configService, llm, mock(AppJobService.class));
        QuestionBankBuild build = new QuestionBankBuild();
        build.setId(14L); build.setScope("PRIVATE"); build.setOwnerUserId(7L); build.setLlmConfigId(3L); build.setCheckpointJson("[]");
        when(buildMapper.selectById(14L)).thenReturn(build);
        KnowledgeSourceFile source = new KnowledgeSourceFile();
        source.setId(24L); source.setFileHash("hash4"); source.setOriginalFilename("notes.txt"); source.setMarkdownStorageKey("text-4");
        when(sourceFileMapper.selectList(any(QueryWrapper.class))).thenReturn(List.of(source));
        when(storage.readText("text-4")).thenReturn("partial chunk");
        when(configService.requireOwnedRuntimeConfig(7L, 3L)).thenReturn(
                new UserLlmRuntimeConfig(3L, 7L, "test", "test", "http://localhost", "mock", "secret", 0.0));
        QuestionBankBuildCandidate partial = new QuestionBankBuildCandidate();
        partial.setId(51L); partial.setBuildId(14L); partial.setChunkIndex(0);
        partial.setSelfCheckJson("{\"status\":\"PENDING\",\"expectedChunkCandidates\":2}");
        when(candidateMapper.selectList(any(QueryWrapper.class))).thenReturn(List.of(partial));
        AppJob job = new AppJob(); job.setId(102L); job.setBuildId(14L); job.setClaimedBy("worker");

        assertThatThrownBy(() -> handler.handle(job)).hasMessageContaining("只落库了部分内容");
        verifyNoInteractions(llm);
        verify(candidateMapper, never()).insert(any());
    }
}
