package com.interview.service.questionbank.build;

import com.alibaba.fastjson2.JSON;
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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;
import java.util.Map;
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
    void shouldRefreshMachineReviewCountsBeforeReviewingTheNextCandidate() {
        QuestionBankBuildMapper buildMapper = mock(QuestionBankBuildMapper.class);
        QuestionBankBuildCandidateMapper candidateMapper = mock(QuestionBankBuildCandidateMapper.class);
        AppJobService appJobService = mock(AppJobService.class);
        QuestionBankBuildSupervisionService supervisionService = mock(QuestionBankBuildSupervisionService.class);
        QuestionBankBuild build = new QuestionBankBuild();
        build.setId(20L); build.setScope("PRIVATE"); build.setStatus("RUNNING");
        QuestionBankBuildCandidate first = new QuestionBankBuildCandidate();
        first.setId(61L); first.setBuildId(20L); first.setChunkIndex(0); first.setReviewStatus("PENDING"); first.setMachineReviewStatus("PENDING");
        QuestionBankBuildCandidate second = new QuestionBankBuildCandidate();
        second.setId(62L); second.setBuildId(20L); second.setChunkIndex(1); second.setReviewStatus("PENDING"); second.setMachineReviewStatus("PENDING");
        when(buildMapper.selectById(20L)).thenReturn(build);
        when(candidateMapper.selectList(any(QueryWrapper.class))).thenReturn(List.of(first, second));
        when(supervisionService.review(any())).thenAnswer(invocation -> {
            QuestionBankBuildSupervisionContext context = invocation.getArgument(0);
            if (context.candidate().getId().equals(61L)) {
                return new QuestionBankBuildSupervisionResult("AUTO_PASS", 0.9, List.of(), Map.of(), null, "{}");
            }
            assertThat(build.getAutoPassCount()).isEqualTo(1);
            return new QuestionBankBuildSupervisionResult("NEEDS_HUMAN", 0.5, List.of("需复核"), Map.of(), null, "{}");
        });
        AppJob job = new AppJob(); job.setId(104L); job.setClaimedBy("worker");

        new QuestionBankBuildSupervisionRunner(buildMapper, candidateMapper, appJobService, supervisionService)
                .run(build, Map.of(0, "source 0", 1, "source 1"), mock(UserLlmRuntimeConfig.class), job);

        assertThat(build.getAutoPassCount()).isEqualTo(1);
        assertThat(build.getNeedsHumanCount()).isEqualTo(1);
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
        QuestionBankBuildSupervisionService supervisionService = mock(QuestionBankBuildSupervisionService.class);
        QuestionBankBuildJobHandler handler = new QuestionBankBuildJobHandler(
                buildMapper, candidateMapper, sourceFileMapper, storage, properties, configService, llm, appJobService, supervisionService);

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
        verify(appJobService).updateRunningJob(99L, "worker", "GENERATING", 50);
        verify(buildMapper, atLeastOnce()).updateById(build);
        org.assertj.core.api.Assertions.assertThat(build.getStatus()).isEqualTo(AppJobService.STATUS_COMPLETED);
    }

    @Test
    void shouldPreserveExistingHumanDecisionWithoutRunningMachineSupervision() throws Exception {
        QuestionBankBuildMapper buildMapper = mock(QuestionBankBuildMapper.class);
        when(buildMapper.update(isNull(), any(UpdateWrapper.class))).thenReturn(1);
        QuestionBankBuildCandidateMapper candidateMapper = mock(QuestionBankBuildCandidateMapper.class);
        KnowledgeSourceFileMapper sourceFileMapper = mock(KnowledgeSourceFileMapper.class);
        QuestionBankBuildFileStorage storage = mock(QuestionBankBuildFileStorage.class);
        QuestionBankBuildProperties properties = new QuestionBankBuildProperties();
        UserLlmConfigService configService = mock(UserLlmConfigService.class);
        QuestionBankBuildLlm llm = mock(QuestionBankBuildLlm.class);
        AppJobService appJobService = mock(AppJobService.class);
        QuestionBankBuildSupervisionService supervisionService = mock(QuestionBankBuildSupervisionService.class);
        QuestionBankBuildJobHandler handler = new QuestionBankBuildJobHandler(
                buildMapper, candidateMapper, sourceFileMapper, storage, properties, configService, llm, appJobService, supervisionService);

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
        candidate.setFollowUpPathsJson("[\"深入\",\"引导\"]"); candidate.setSourceRef("notes.md#chunk-0"); candidate.setSourceEvidenceJson("[{\"quote\":\"evidence\"}]"); candidate.setSelfCheckJson("{\"passed\":true}"); candidate.setMachineReviewStatus("SKIPPED"); candidate.setReviewStatus("ACCEPTED");
        when(candidateMapper.selectList(any(QueryWrapper.class))).thenReturn(List.of(candidate));
        when(candidateMapper.selectCount(any(QueryWrapper.class))).thenReturn(1L, 0L, 0L);
        AppJob job = new AppJob(); job.setId(99L); job.setBuildId(11L); job.setClaimedBy("worker");

        handler.handle(job);

        verify(llm, never()).complete(any(), anyString(), anyString());
        verifyNoInteractions(supervisionService);
        verify(candidateMapper, never()).insert(any());
        assertThat(candidate.getSubject()).isEqualTo("人工修订后的主题");
    }

    @Test
    void shouldResumeExistingCandidateOnlyForMissingMachineSupervision() throws Exception {
        QuestionBankBuildMapper buildMapper = mock(QuestionBankBuildMapper.class);
        when(buildMapper.update(isNull(), any(UpdateWrapper.class))).thenReturn(1);
        QuestionBankBuildCandidateMapper candidateMapper = mock(QuestionBankBuildCandidateMapper.class);
        KnowledgeSourceFileMapper sourceFileMapper = mock(KnowledgeSourceFileMapper.class);
        QuestionBankBuildFileStorage storage = mock(QuestionBankBuildFileStorage.class);
        QuestionBankBuildProperties properties = new QuestionBankBuildProperties();
        UserLlmConfigService configService = mock(UserLlmConfigService.class);
        QuestionBankBuildLlm llm = mock(QuestionBankBuildLlm.class);
        AppJobService appJobService = mock(AppJobService.class);
        QuestionBankBuildSupervisionService supervisionService = mock(QuestionBankBuildSupervisionService.class);
        QuestionBankBuildJobHandler handler = new QuestionBankBuildJobHandler(
                buildMapper, candidateMapper, sourceFileMapper, storage, properties, configService, llm, appJobService, supervisionService);
        QuestionBankBuild build = new QuestionBankBuild(); build.setId(12L); build.setScope("PRIVATE"); build.setOwnerUserId(7L); build.setLlmConfigId(3L); build.setCheckpointJson("[]");
        when(buildMapper.selectById(12L)).thenReturn(build);
        KnowledgeSourceFile source = new KnowledgeSourceFile(); source.setId(22L); source.setFileHash("hash2"); source.setOriginalFilename("notes.md"); source.setMarkdownStorageKey("text");
        when(sourceFileMapper.selectList(any(QueryWrapper.class))).thenReturn(List.of(source)); when(storage.readText("text")).thenReturn("one chunk");
        when(configService.requireOwnedRuntimeConfig(7L, 3L)).thenReturn(new UserLlmRuntimeConfig(3L, 7L, "test", "test", "http://localhost", "mock", "secret", 0.0));
        QuestionBankBuildCandidate candidate = new QuestionBankBuildCandidate(); candidate.setId(42L); candidate.setBuildId(12L); candidate.setChunkIndex(0); candidate.setSubject("主题"); candidate.setCategory("java"); candidate.setDifficulty("mid"); candidate.setPrinciples("答案"); candidate.setFollowUpPathsJson("[\"深入\",\"引导\"]"); candidate.setSourceRef("notes"); candidate.setSourceEvidenceJson("[{\"quote\":\"evidence\"}]"); candidate.setReviewStatus("PENDING");
        when(candidateMapper.selectList(any(QueryWrapper.class))).thenReturn(List.of(candidate)); when(candidateMapper.selectCount(any(QueryWrapper.class))).thenReturn(1L, 0L, 0L);
        when(supervisionService.review(any())).thenReturn(new QuestionBankBuildSupervisionResult(
                "AUTO_PASS", 0.9, List.of(), java.util.Map.of(), null, "{\"verdict\":\"PASS\"}"));
        AppJob job = new AppJob(); job.setId(100L); job.setBuildId(12L); job.setClaimedBy("worker");

        handler.handle(job);

        verifyNoInteractions(llm);
        verify(supervisionService).review(any());
        assertThat(candidate.getMachineReviewStatus()).isEqualTo("AUTO_PASS");
        assertThat(build.getStage()).isEqualTo("READY_FOR_FINAL_REVIEW");
        verify(candidateMapper, never()).insert(any());
    }

    @Test
    void shouldPersistGeneratedCandidateAndRouteSupervisionFailureToHumanWithoutAnotherModelCall() throws Exception {
        QuestionBankBuildMapper buildMapper = mock(QuestionBankBuildMapper.class);
        when(buildMapper.update(isNull(), any(UpdateWrapper.class))).thenReturn(1);
        QuestionBankBuildCandidateMapper candidateMapper = mock(QuestionBankBuildCandidateMapper.class);
        KnowledgeSourceFileMapper sourceFileMapper = mock(KnowledgeSourceFileMapper.class);
        QuestionBankBuildFileStorage storage = mock(QuestionBankBuildFileStorage.class);
        UserLlmConfigService configService = mock(UserLlmConfigService.class);
        QuestionBankBuildLlm llm = mock(QuestionBankBuildLlm.class);
        AppJobService appJobService = mock(AppJobService.class);
        QuestionBankBuildSupervisionService supervisionService = mock(QuestionBankBuildSupervisionService.class);
        QuestionBankBuildJobHandler handler = new QuestionBankBuildJobHandler(
                buildMapper, candidateMapper, sourceFileMapper, storage,
                new QuestionBankBuildProperties(), configService, llm, appJobService, supervisionService);

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
        when(llm.complete(any(), contains("知识原子生成器"), anyString())).thenReturn(
                """
                {"subject":"类加载","category":"jvm","difficulty":"mid","tags":[],
                 "content":{"principles":"双亲委派","pitfalls":"破坏隔离",
                 "follow_up_paths":["深入","引导"]},
                 "sourceEvidence":"证据句 A：JVM class loading"}
                """);
        when(supervisionService.review(any()))
                .thenThrow(new IllegalStateException("temporary supervision failure"));
        AppJob job = new AppJob(); job.setId(101L); job.setBuildId(13L); job.setClaimedBy("worker");

        handler.handle(job);
        assertThat(persisted.get()).isNotNull();
        assertThat(persisted.get().getMachineReviewStatus()).isEqualTo("NEEDS_HUMAN");
        assertThat(persisted.get().getSourceEvidenceJson()).contains("证据句 A：JVM class loading");
        verify(llm, times(1)).complete(any(), contains("知识原子生成器"), anyString());
        verify(supervisionService, times(1)).review(any());
        verify(candidateMapper, times(1)).insert(any(QuestionBankBuildCandidate.class));
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
        QuestionBankBuildSupervisionService supervisionService = mock(QuestionBankBuildSupervisionService.class);
        QuestionBankBuildJobHandler handler = new QuestionBankBuildJobHandler(
                buildMapper, candidateMapper, sourceFileMapper, storage,
                new QuestionBankBuildProperties(), configService, llm, mock(AppJobService.class), supervisionService);
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
        verifyNoInteractions(supervisionService);
        verify(candidateMapper, never()).insert(any());
    }

    @Test
    void shouldNotRepeatAnUnconfirmedGenerationCallDuringAutomaticRecovery() throws Exception {
        QuestionBankBuildMapper buildMapper = mock(QuestionBankBuildMapper.class);
        when(buildMapper.update(isNull(), any(UpdateWrapper.class))).thenReturn(1);
        QuestionBankBuildCandidateMapper candidateMapper = mock(QuestionBankBuildCandidateMapper.class);
        KnowledgeSourceFileMapper sourceFileMapper = mock(KnowledgeSourceFileMapper.class);
        QuestionBankBuildFileStorage storage = mock(QuestionBankBuildFileStorage.class);
        UserLlmConfigService configService = mock(UserLlmConfigService.class);
        QuestionBankBuildLlm llm = mock(QuestionBankBuildLlm.class);
        QuestionBankBuildSupervisionService supervision = mock(QuestionBankBuildSupervisionService.class);
        QuestionBankBuildJobHandler handler = new QuestionBankBuildJobHandler(
                buildMapper, candidateMapper, sourceFileMapper, storage,
                new QuestionBankBuildProperties(), configService, llm, mock(AppJobService.class), supervision);
        QuestionBankBuild build = new QuestionBankBuild();
        build.setId(15L); build.setScope("PRIVATE"); build.setOwnerUserId(7L); build.setLlmConfigId(3L);
        build.setStatus("RUNNING"); build.setChunkCount(1);
        build.setCheckpointJson("{\"completedChunkIndexes\":[],\"generation\":{\"chunkIndex\":0,\"startedRetryCount\":0,\"response\":null}}");
        when(buildMapper.selectById(15L)).thenReturn(build);
        KnowledgeSourceFile source = new KnowledgeSourceFile();
        source.setId(25L); source.setOriginalFilename("notes.txt"); source.setMarkdownStorageKey("text-5");
        when(sourceFileMapper.selectList(any(QueryWrapper.class))).thenReturn(List.of(source));
        when(storage.readText("text-5")).thenReturn("uncertain generation");
        when(configService.requireOwnedRuntimeConfig(7L, 3L)).thenReturn(
                new UserLlmRuntimeConfig(3L, 7L, "test", "test", "http://localhost", "mock", "secret", 0.0));
        AppJob job = new AppJob(); job.setId(103L); job.setBuildId(15L); job.setClaimedBy("worker"); job.setRetryCount(0);

        assertThatThrownBy(() -> handler.handle(job)).hasMessageContaining("避免重复计费");
        verifyNoInteractions(llm, supervision);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "not-json",
            "{\"atoms\":[{\"subject\":\"完整题目\",\"content\":{\"principles\":\"有效回答\",\"followUpPaths\":[\"深入\",\"引导\"]}},{\"subject\":\"字段不完整的题目\",\"content\":{\"principles\":\"有效回答\",\"followUpPaths\":[\"只有一条\"]}}]}"
    })
    void shouldRetryPersistedInvalidGenerationOnlyAfterExplicitRetry(String invalidGeneration) throws Exception {
        QuestionBankBuildMapper buildMapper = mock(QuestionBankBuildMapper.class);
        when(buildMapper.update(isNull(), any(UpdateWrapper.class))).thenReturn(1);
        QuestionBankBuildCandidateMapper candidateMapper = mock(QuestionBankBuildCandidateMapper.class);
        KnowledgeSourceFileMapper sourceFileMapper = mock(KnowledgeSourceFileMapper.class);
        QuestionBankBuildFileStorage storage = mock(QuestionBankBuildFileStorage.class);
        UserLlmConfigService configService = mock(UserLlmConfigService.class);
        QuestionBankBuildLlm llm = mock(QuestionBankBuildLlm.class);
        AppJobService appJobService = mock(AppJobService.class);
        QuestionBankBuildSupervisionService supervision = mock(QuestionBankBuildSupervisionService.class);
        QuestionBankBuildJobHandler handler = new QuestionBankBuildJobHandler(
                buildMapper, candidateMapper, sourceFileMapper, storage,
                new QuestionBankBuildProperties(), configService, llm, appJobService, supervision);
        QuestionBankBuild build = new QuestionBankBuild();
        build.setId(17L); build.setScope("PRIVATE"); build.setOwnerUserId(7L); build.setLlmConfigId(3L);
        build.setStatus("FAILED"); build.setChunkCount(1); build.setProgress(100);
        QuestionBankBuildCheckpoint checkpoint = QuestionBankBuildCheckpoint.parse(null);
        checkpoint.startGeneration(0, 0);
        checkpoint.persistGenerationResponse(invalidGeneration);
        build.setCheckpointJson(checkpoint.toJson());
        when(buildMapper.selectById(17L)).thenReturn(build);
        KnowledgeSourceFile source = new KnowledgeSourceFile();
        source.setId(27L); source.setFileHash("hash7"); source.setOriginalFilename("notes.txt"); source.setMarkdownStorageKey("text-7");
        when(sourceFileMapper.selectList(any(QueryWrapper.class))).thenReturn(List.of(source));
        when(storage.readText("text-7")).thenReturn("retry invalid generation");
        UserLlmRuntimeConfig runtime = new UserLlmRuntimeConfig(
                3L, 7L, "test", "test", "http://localhost", "mock", "secret", 0.0);
        when(configService.requireOwnedRuntimeConfig(7L, 3L)).thenReturn(runtime);
        when(llm.complete(any(), contains("知识原子生成器"), anyString())).thenReturn(
                """
                {"subject":"重试题","category":"java","difficulty":"mid","tags":[],
                 "content":{"principles":"有效回答","pitfalls":"误区",
                 "followUpPaths":["深入","引导"]},
                 "sourceEvidence":[{"quote":"retry invalid generation"}]}
                """);
        AtomicReference<QuestionBankBuildCandidate> persisted = new AtomicReference<>();
        when(candidateMapper.selectList(any(QueryWrapper.class))).thenAnswer(invocation ->
                persisted.get() == null ? List.of() : List.of(persisted.get()));
        doAnswer(invocation -> {
            QuestionBankBuildCandidate inserted = invocation.getArgument(0);
            inserted.setId(71L);
            persisted.set(inserted);
            return 1;
        }).when(candidateMapper).insert(any(QuestionBankBuildCandidate.class));
        when(candidateMapper.selectCount(any(QueryWrapper.class))).thenAnswer(invocation -> persisted.get() == null ? 0L : 1L);
        when(supervision.review(any())).thenReturn(new QuestionBankBuildSupervisionResult(
                "AUTO_PASS", 0.9, List.of(), Map.of(), null, "{}"));
        AppJob job = new AppJob();
        job.setId(105L); job.setBuildId(17L); job.setClaimedBy("worker"); job.setRetryCount(1);

        handler.handle(job);

        verify(llm, times(1)).complete(any(), contains("知识原子生成器"), anyString());
        verify(candidateMapper, times(1)).insert(any(QuestionBankBuildCandidate.class));
        assertThat(persisted.get()).isNotNull();
        assertThat(build.getStatus()).isEqualTo(AppJobService.STATUS_COMPLETED);
        assertThat(JSON.parseObject(build.getCheckpointJson())
                .getJSONObject("lastRejectedGeneration")
                .getString("response")).isEqualTo(invalidGeneration);
    }

    @Test
    void shouldFailClosedWhenProviderOrModelChangedAfterBuildCreation() {
        QuestionBankBuildMapper buildMapper = mock(QuestionBankBuildMapper.class);
        when(buildMapper.update(isNull(), any(UpdateWrapper.class))).thenReturn(1);
        UserLlmConfigService configService = mock(UserLlmConfigService.class);
        QuestionBankBuildLlm llm = mock(QuestionBankBuildLlm.class);
        QuestionBankBuildJobHandler handler = new QuestionBankBuildJobHandler(
                buildMapper, mock(QuestionBankBuildCandidateMapper.class), mock(KnowledgeSourceFileMapper.class),
                mock(QuestionBankBuildFileStorage.class), new QuestionBankBuildProperties(), configService, llm,
                mock(AppJobService.class), mock(QuestionBankBuildSupervisionService.class));
        QuestionBankBuild build = new QuestionBankBuild();
        build.setId(16L); build.setScope("PRIVATE"); build.setOwnerUserId(7L); build.setLlmConfigId(3L);
        build.setLlmProvider("openai"); build.setLlmModel("gpt-old");
        when(buildMapper.selectById(16L)).thenReturn(build);
        when(configService.requireOwnedRuntimeConfig(7L, 3L)).thenReturn(
                new UserLlmRuntimeConfig(3L, 7L, "openai", "test", "http://localhost", "gpt-new", "secret", 0.0));
        AppJob job = new AppJob(); job.setId(104L); job.setBuildId(16L); job.setClaimedBy("worker");

        assertThatThrownBy(() -> handler.handle(job)).hasMessageContaining("构建快照不一致");
        verifyNoInteractions(llm);
    }
}
