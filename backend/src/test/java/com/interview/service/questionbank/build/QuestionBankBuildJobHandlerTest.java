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
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
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
        when(candidateMapper.update(any(QuestionBankBuildCandidate.class), any(UpdateWrapper.class))).thenReturn(1);
        when(supervisionService.complete(any())).thenAnswer(invocation -> {
            QuestionBankBuildSupervisionContext context = invocation.getArgument(0);
            return context.candidate().getId().toString();
        });
        when(supervisionService.parse(anyString())).thenAnswer(invocation -> {
            if ("61".equals(invocation.getArgument(0))) {
                return new QuestionBankBuildSupervisionResult("AUTO_PASS", 0.9, List.of(), Map.of(), null, "{}");
            }
            assertThat(build.getAutoPassCount()).isEqualTo(1);
            return new QuestionBankBuildSupervisionResult("NEEDS_HUMAN", 0.5, List.of("需复核"), Map.of(), null, "{}");
        });
        AppJob job = new AppJob(); job.setId(104L); job.setClaimedBy("worker");
        when(appJobService.extendRunningJobLease(eq(104L), eq("worker"), any())).thenReturn(true);
        QuestionBankBuildProperties properties = new QuestionBankBuildProperties();
        properties.setMaxRepairRounds(0);

        new QuestionBankBuildSupervisionRunner(buildMapper, candidateMapper, appJobService, supervisionService,
                mock(QuestionBankBuildRepairService.class), mock(QuestionBankBuildCandidateValidator.class), properties)
                .run(build, Map.of(0, "source 0", 1, "source 1"), mock(UserLlmRuntimeConfig.class), job,
                        QuestionBankBuildCheckpoint.parse(null));

        assertThat(build.getAutoPassCount()).isEqualTo(1);
        assertThat(build.getNeedsHumanCount()).isEqualTo(1);
    }

    @Test
    void shouldRepairAndResuperviseCandidateBeforeFinalReview() {
        QuestionBankBuildMapper buildMapper = mock(QuestionBankBuildMapper.class);
        QuestionBankBuildCandidateMapper candidateMapper = mock(QuestionBankBuildCandidateMapper.class);
        AppJobService appJobService = mock(AppJobService.class);
        QuestionBankBuildSupervisionService supervisionService = mock(QuestionBankBuildSupervisionService.class);
        QuestionBankBuildLlm llm = mock(QuestionBankBuildLlm.class);
        QuestionBankBuildProperties properties = new QuestionBankBuildProperties();
        properties.setMaxRepairRounds(2);
        QuestionBankBuildCandidateValidator validator = new QuestionBankBuildCandidateValidator();
        QuestionBankBuildSupervisionRunner runner = new QuestionBankBuildSupervisionRunner(
                buildMapper, candidateMapper, appJobService, supervisionService,
                new QuestionBankBuildRepairService(llm, validator), validator, properties);
        QuestionBankBuild build = runningBuild(30L);
        QuestionBankBuildCandidate candidate = validCandidate(71L, 30L);
        when(buildMapper.selectById(30L)).thenReturn(build);
        when(candidateMapper.selectList(any(QueryWrapper.class))).thenReturn(List.of(candidate));
        when(candidateMapper.update(any(QuestionBankBuildCandidate.class), any(UpdateWrapper.class))).thenReturn(1);
        when(appJobService.extendRunningJobLease(eq(201L), eq("worker"), any())).thenReturn(true);
        stubSupervision(supervisionService,
                new QuestionBankBuildSupervisionResult(
                        "NEEDS_HUMAN", 0.6, List.of("主题过于宽泛"),
                        Map.of("subject", "JVM 类加载机制"), null, "{}"),
                new QuestionBankBuildSupervisionResult(
                        "AUTO_PASS", 0.96, List.of(), Map.of(), null, "{}"));
        when(llm.complete(any(), contains("修复助手"), anyString())).thenReturn(
                """
                {"action":"UPDATE","summary":"收窄主题并保留原文依据","candidate":{
                  "subject":"JVM 类加载机制","category":"jvm","difficulty":"mid",
                  "tags":["JVM"],"principles":"双亲委派模型",
                  "pitfalls":"混淆加载与初始化","followUpPaths":["深入追问","引导追问"]}}
                """);
        AppJob job = new AppJob();
        job.setId(201L); job.setClaimedBy("worker"); job.setRetryCount(0);

        runner.run(build, Map.of(0, "JVM 通过双亲委派完成类加载"),
                mock(UserLlmRuntimeConfig.class), job, QuestionBankBuildCheckpoint.parse(null));

        assertThat(candidate.getSubject()).isEqualTo("JVM 类加载机制");
        assertThat(candidate.getMachineReviewStatus()).isEqualTo("AUTO_PASS");
        assertThat(candidate.getRepairStatus()).isEqualTo("VERIFIED");
        assertThat(candidate.getRepairAttempts()).isEqualTo(1);
        assertThat(candidate.getRepairHistoryJson()).contains("收窄主题");
        assertThat(build.getRepairedCount()).isEqualTo(1);
        verify(supervisionService, times(2)).complete(any());
        verify(llm, times(1)).complete(any(), contains("修复助手"), anyString());
    }

    @Test
    void shouldFailClosedOnInvalidRepairResponseWithoutAutomaticPaidRetry() {
        QuestionBankBuildMapper buildMapper = mock(QuestionBankBuildMapper.class);
        QuestionBankBuildCandidateMapper candidateMapper = mock(QuestionBankBuildCandidateMapper.class);
        AppJobService appJobService = mock(AppJobService.class);
        QuestionBankBuildSupervisionService supervisionService = mock(QuestionBankBuildSupervisionService.class);
        QuestionBankBuildLlm llm = mock(QuestionBankBuildLlm.class);
        QuestionBankBuildProperties properties = new QuestionBankBuildProperties();
        properties.setMaxRepairRounds(2);
        QuestionBankBuildCandidateValidator validator = new QuestionBankBuildCandidateValidator();
        QuestionBankBuildSupervisionRunner runner = new QuestionBankBuildSupervisionRunner(
                buildMapper, candidateMapper, appJobService, supervisionService,
                new QuestionBankBuildRepairService(llm, validator), validator, properties);
        QuestionBankBuild build = runningBuild(31L);
        QuestionBankBuildCandidate candidate = validCandidate(72L, 31L);
        when(buildMapper.selectById(31L)).thenReturn(build);
        when(candidateMapper.selectList(any(QueryWrapper.class))).thenReturn(List.of(candidate));
        when(candidateMapper.update(any(QuestionBankBuildCandidate.class), any(UpdateWrapper.class))).thenReturn(1);
        when(appJobService.extendRunningJobLease(eq(202L), eq("worker"), any())).thenReturn(true);
        stubSupervision(supervisionService, new QuestionBankBuildSupervisionResult(
                "NEEDS_HUMAN", 0.4, List.of("证据不足"), Map.of(), null, "{}"));
        when(llm.complete(any(), contains("修复助手"), anyString())).thenReturn("not-json");
        AppJob job = new AppJob();
        job.setId(202L); job.setClaimedBy("worker"); job.setRetryCount(0);

        QuestionBankBuildCheckpoint checkpoint = QuestionBankBuildCheckpoint.parse(null);
        assertThatThrownBy(() -> runner.run(build, Map.of(0, "JVM 文档片段"),
                mock(UserLlmRuntimeConfig.class), job, checkpoint))
                .hasMessageContaining("有效 JSON");

        assertThat(candidate.getRepairStatus()).isEqualTo("RUNNING");
        assertThat(candidate.getRepairAttempts()).isEqualTo(1);
        assertThat(checkpoint.repair()).isNotNull();
        assertThat(checkpoint.repair().response()).isEqualTo("not-json");
        verify(llm, times(1)).complete(any(), contains("修复助手"), anyString());
    }

    @Test
    void shouldTreatEmptyRepairResponseAsDeterministicFailureAndRecallOnlyAfterExplicitRetry() {
        QuestionBankBuildMapper buildMapper = mock(QuestionBankBuildMapper.class);
        QuestionBankBuildCandidateMapper candidateMapper = mock(QuestionBankBuildCandidateMapper.class);
        AppJobService appJobService = mock(AppJobService.class);
        QuestionBankBuildSupervisionService supervision = mock(QuestionBankBuildSupervisionService.class);
        QuestionBankBuildLlm llm = mock(QuestionBankBuildLlm.class);
        QuestionBankBuildProperties properties = new QuestionBankBuildProperties();
        properties.setMaxRepairRounds(1);
        QuestionBankBuildCandidateValidator validator = new QuestionBankBuildCandidateValidator();
        QuestionBankBuild build = runningBuild(32L);
        QuestionBankBuildCandidate candidate = validCandidate(73L, 32L);
        candidate.setMachineReviewStatus("NEEDS_HUMAN"); candidate.setRepairStatus("PENDING");
        when(buildMapper.selectById(32L)).thenReturn(build);
        when(candidateMapper.selectById(73L)).thenReturn(candidate);
        when(candidateMapper.selectList(any(QueryWrapper.class))).thenReturn(List.of(candidate));
        when(candidateMapper.update(any(QuestionBankBuildCandidate.class), any(UpdateWrapper.class))).thenReturn(1);
        when(appJobService.extendRunningJobLease(eq(203L), eq("worker"), any())).thenReturn(true);
        when(llm.complete(any(), contains("修复助手"), anyString()))
                .thenReturn("")
                .thenReturn("""
                        {"action":"UPDATE","summary":"显式重试后修复","candidate":{
                          "subject":"JVM 类加载机制","category":"jvm","difficulty":"mid",
                          "tags":["JVM"],"principles":"双亲委派模型",
                          "pitfalls":"混淆加载与初始化","followUpPaths":["深入追问","引导追问"]}}
                        """);
        stubSupervision(supervision, new QuestionBankBuildSupervisionResult(
                "AUTO_PASS", 0.96, List.of(), Map.of(), null, "pass"));
        AppJob job = new AppJob(); job.setId(203L); job.setClaimedBy("worker"); job.setRetryCount(0);
        QuestionBankBuildCheckpoint checkpoint = QuestionBankBuildCheckpoint.parse(null);
        QuestionBankBuildSupervisionRunner runner = new QuestionBankBuildSupervisionRunner(
                buildMapper, candidateMapper, appJobService, supervision,
                new QuestionBankBuildRepairService(llm, validator), validator, properties);

        assertThatThrownBy(() -> runner.run(build, Map.of(0, "JVM 文档片段"),
                mock(UserLlmRuntimeConfig.class), job, checkpoint))
                .hasMessage("修复助手结果不是有效 JSON");
        assertThat(checkpoint.repair().response()).isEmpty();

        assertThatThrownBy(() -> runner.run(build, Map.of(0, "JVM 文档片段"),
                mock(UserLlmRuntimeConfig.class), job, checkpoint))
                .hasMessage("修复助手结果不是有效 JSON");
        verify(llm, times(1)).complete(any(), contains("修复助手"), anyString());

        job.setRetryCount(1);
        runner.run(build, Map.of(0, "JVM 文档片段"),
                mock(UserLlmRuntimeConfig.class), job, checkpoint);

        verify(llm, times(2)).complete(any(), contains("修复助手"), anyString());
        assertThat(checkpoint.repair()).isNull();
        assertThat(candidate.getRepairStatus()).isEqualTo("VERIFIED");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"null", "   "})
    void repairParserShouldRejectStructuredEmptyResponseWithStableError(String raw) {
        QuestionBankBuildRepairService service = new QuestionBankBuildRepairService(
                mock(QuestionBankBuildLlm.class), new QuestionBankBuildCandidateValidator());

        assertThatThrownBy(() -> service.parse(validCandidate(74L, 33L), raw))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("修复助手结果不是有效 JSON");
    }

    @Test
    void shouldParseUploadedSourceInsideJobBeforeGeneration() throws Exception {
        QuestionBankBuildMapper buildMapper = mock(QuestionBankBuildMapper.class);
        when(buildMapper.update(isNull(), any(UpdateWrapper.class))).thenReturn(1);
        QuestionBankBuildCandidateMapper candidateMapper = mock(QuestionBankBuildCandidateMapper.class);
        KnowledgeSourceFileMapper sourceFileMapper = mock(KnowledgeSourceFileMapper.class);
        QuestionBankBuildFileStorage storage = mock(QuestionBankBuildFileStorage.class);
        UserLlmConfigService configService = mock(UserLlmConfigService.class);
        QuestionBankBuildLlm llm = mock(QuestionBankBuildLlm.class);
        AppJobService appJobService = mock(AppJobService.class);
        QuestionBankBuildSupervisionService supervision = mock(QuestionBankBuildSupervisionService.class);
        QuestionBankBuildJobHandler handler = handler(
                buildMapper, candidateMapper, sourceFileMapper, storage,
                new QuestionBankBuildProperties(), configService, llm, appJobService, supervision);
        QuestionBankBuild build = new QuestionBankBuild();
        build.setId(18L); build.setScope("PRIVATE"); build.setOwnerUserId(7L); build.setLlmConfigId(3L);
        build.setStatus("PENDING"); build.setStage("QUEUED"); build.setCheckpointJson("[]");
        when(buildMapper.selectById(18L)).thenReturn(build);
        KnowledgeSourceFile source = new KnowledgeSourceFile();
        source.setId(28L); source.setFileHash("hash8"); source.setOriginalFilename("notes.md");
        source.setStorageKey("builds/18/original/notes.md"); source.setStatus("UPLOADED");
        when(sourceFileMapper.selectList(any(QueryWrapper.class))).thenReturn(List.of(source));
        when(storage.readBytes(source.getStorageKey())).thenReturn("JVM 通过双亲委派加载类".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        when(storage.storeText(18L, 28L, "JVM 通过双亲委派加载类")).thenReturn("builds/18/text/28.txt");
        when(storage.readText("builds/18/text/28.txt")).thenReturn("JVM 通过双亲委派加载类");
        when(configService.requireOwnedRuntimeConfig(7L, 3L)).thenReturn(
                new UserLlmRuntimeConfig(3L, 7L, "test", "test", "http://localhost", "mock", "secret", 0.0));
        when(llm.complete(any(), contains("知识原子生成器"), anyString())).thenAnswer(invocation -> {
            assertThat(build.getStage()).isEqualTo("GENERATING");
            return """
                {"atoms":[{"subject":"JVM 类加载","category":"jvm","difficulty":"mid","tags":["JVM"],
                  "content":{"principles":"双亲委派","pitfalls":"混淆阶段","followUpPaths":["深入追问","引导追问"]},
                  "sourceEvidence":[{"quote":"JVM 通过双亲委派加载类"}]}]}
                """;
        });
        AtomicReference<QuestionBankBuildCandidate> persisted = new AtomicReference<>();
        when(candidateMapper.selectList(any(QueryWrapper.class))).thenAnswer(invocation ->
                persisted.get() == null ? List.of() : List.of(persisted.get()));
        doAnswer(invocation -> {
            QuestionBankBuildCandidate inserted = invocation.getArgument(0);
            inserted.setId(81L); persisted.set(inserted); return 1;
        }).when(candidateMapper).insert(any(QuestionBankBuildCandidate.class));
        when(candidateMapper.selectCount(any(QueryWrapper.class))).thenAnswer(
                invocation -> persisted.get() == null ? 0L : 1L);
        stubSupervision(supervision, new QuestionBankBuildSupervisionResult(
                "AUTO_PASS", 0.95, List.of(), Map.of(), null, "{}"));
        AppJob job = new AppJob(); job.setId(106L); job.setBuildId(18L); job.setClaimedBy("worker");
        bindArtifacts(build, source, job);

        handler.handle(job);

        assertThat(source.getStatus()).isEqualTo("CONVERTED");
        assertThat(source.getMarkdownStorageKey()).isEqualTo("builds/18/text/28.txt");
        assertThat(build.getChunkCount()).isEqualTo(1);
        assertThat(build.getStage()).isEqualTo("READY_FOR_FINAL_REVIEW");
        verify(storage).readBytes("builds/18/original/notes.md");
        verify(storage).storeText(18L, 28L, "JVM 通过双亲委派加载类");
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
        QuestionBankBuildJobHandler handler = handler(
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
        bindArtifacts(build, source, job);
        handler.handle(job);

        verify(llm, never()).complete(any(), anyString(), anyString());
        verify(appJobService).updateRunningJob(99L, "worker", "PARSING", 50);
        verify(buildMapper, atLeastOnce()).update(isNull(), any(UpdateWrapper.class));
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
        QuestionBankBuildJobHandler handler = handler(
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
        bindArtifacts(build, source, job);

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
        QuestionBankBuildJobHandler handler = handler(
                buildMapper, candidateMapper, sourceFileMapper, storage, properties, configService, llm, appJobService, supervisionService);
        QuestionBankBuild build = new QuestionBankBuild(); build.setId(12L); build.setScope("PRIVATE"); build.setOwnerUserId(7L); build.setLlmConfigId(3L); build.setCheckpointJson("[]");
        when(buildMapper.selectById(12L)).thenReturn(build);
        KnowledgeSourceFile source = new KnowledgeSourceFile(); source.setId(22L); source.setFileHash("hash2"); source.setOriginalFilename("notes.md"); source.setMarkdownStorageKey("text");
        when(sourceFileMapper.selectList(any(QueryWrapper.class))).thenReturn(List.of(source)); when(storage.readText("text")).thenReturn("one chunk");
        when(configService.requireOwnedRuntimeConfig(7L, 3L)).thenReturn(new UserLlmRuntimeConfig(3L, 7L, "test", "test", "http://localhost", "mock", "secret", 0.0));
        QuestionBankBuildCandidate candidate = new QuestionBankBuildCandidate(); candidate.setId(42L); candidate.setBuildId(12L); candidate.setChunkIndex(0); candidate.setSubject("主题"); candidate.setCategory("java"); candidate.setDifficulty("mid"); candidate.setPrinciples("答案"); candidate.setFollowUpPathsJson("[\"深入\",\"引导\"]"); candidate.setSourceRef("notes"); candidate.setSourceEvidenceJson("[{\"quote\":\"evidence\"}]"); candidate.setReviewStatus("PENDING");
        when(candidateMapper.selectList(any(QueryWrapper.class))).thenReturn(List.of(candidate)); when(candidateMapper.selectCount(any(QueryWrapper.class))).thenReturn(1L, 0L, 0L);
        stubSupervision(supervisionService, new QuestionBankBuildSupervisionResult(
                "AUTO_PASS", 0.9, List.of(), java.util.Map.of(), null, "{\"verdict\":\"PASS\"}"));
        AppJob job = new AppJob(); job.setId(100L); job.setBuildId(12L); job.setClaimedBy("worker");
        bindArtifacts(build, source, job);

        handler.handle(job);

        verifyNoInteractions(llm);
        verify(supervisionService).complete(any());
        assertThat(candidate.getMachineReviewStatus()).isEqualTo("AUTO_PASS");
        assertThat(build.getStage()).isEqualTo("READY_FOR_FINAL_REVIEW");
        verify(candidateMapper, never()).insert(any());
    }

    @Test
    void shouldPersistGeneratedCandidateButFailJobOnSupervisionInfrastructureFailure() throws Exception {
        QuestionBankBuildMapper buildMapper = mock(QuestionBankBuildMapper.class);
        when(buildMapper.update(isNull(), any(UpdateWrapper.class))).thenReturn(1);
        QuestionBankBuildCandidateMapper candidateMapper = mock(QuestionBankBuildCandidateMapper.class);
        KnowledgeSourceFileMapper sourceFileMapper = mock(KnowledgeSourceFileMapper.class);
        QuestionBankBuildFileStorage storage = mock(QuestionBankBuildFileStorage.class);
        UserLlmConfigService configService = mock(UserLlmConfigService.class);
        QuestionBankBuildLlm llm = mock(QuestionBankBuildLlm.class);
        AppJobService appJobService = mock(AppJobService.class);
        QuestionBankBuildSupervisionService supervisionService = mock(QuestionBankBuildSupervisionService.class);
        QuestionBankBuildJobHandler handler = handler(
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
        when(supervisionService.complete(any()))
                .thenThrow(new IllegalStateException("temporary supervision failure"));
        AppJob job = new AppJob(); job.setId(101L); job.setBuildId(13L); job.setClaimedBy("worker");
        bindArtifacts(build, source, job);

        assertThatThrownBy(() -> handler.handle(job))
                .hasMessageContaining("temporary supervision failure");
        assertThat(persisted.get()).isNotNull();
        assertThat(persisted.get().getMachineReviewStatus()).isEqualTo("RUNNING");
        assertThat(persisted.get().getSourceEvidenceJson()).contains("证据句 A：JVM class loading");
        verify(llm, times(1)).complete(any(), contains("知识原子生成器"), anyString());
        verify(supervisionService, times(1)).complete(any());
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
        AppJobService appJobService = mock(AppJobService.class);
        QuestionBankBuildJobHandler handler = handler(
                buildMapper, candidateMapper, sourceFileMapper, storage,
                new QuestionBankBuildProperties(), configService, llm, appJobService, supervisionService);
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
        bindArtifacts(build, source, job);

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
        AppJobService appJobService = mock(AppJobService.class);
        QuestionBankBuildJobHandler handler = handler(
                buildMapper, candidateMapper, sourceFileMapper, storage,
                new QuestionBankBuildProperties(), configService, llm, appJobService, supervision);
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
        bindArtifacts(build, source, job);

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
        QuestionBankBuildJobHandler handler = handler(
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
        stubSupervision(supervision, new QuestionBankBuildSupervisionResult(
                "AUTO_PASS", 0.9, List.of(), Map.of(), null, "{}"));
        AppJob job = new AppJob();
        job.setId(105L); job.setBuildId(17L); job.setClaimedBy("worker"); job.setRetryCount(1);
        bindArtifacts(build, source, job);

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
        AppJobService appJobService = mock(AppJobService.class);
        QuestionBankBuildJobHandler handler = handler(
                buildMapper, mock(QuestionBankBuildCandidateMapper.class), mock(KnowledgeSourceFileMapper.class),
                mock(QuestionBankBuildFileStorage.class), new QuestionBankBuildProperties(), configService, llm,
                appJobService, mock(QuestionBankBuildSupervisionService.class));
        QuestionBankBuild build = new QuestionBankBuild();
        build.setId(16L); build.setScope("PRIVATE"); build.setOwnerUserId(7L); build.setLlmConfigId(3L);
        build.setLlmProvider("openai"); build.setLlmModel("gpt-old");
        when(buildMapper.selectById(16L)).thenReturn(build);
        when(configService.requireOwnedRuntimeConfig(7L, 3L)).thenReturn(
                new UserLlmRuntimeConfig(3L, 7L, "openai", "test", "http://localhost", "gpt-new", "secret", 0.0));
        AppJob job = new AppJob(); job.setId(104L); job.setBuildId(16L); job.setClaimedBy("worker");
        KnowledgeSourceFile source = new KnowledgeSourceFile();
        bindArtifacts(build, source, job);

        assertThatThrownBy(() -> handler.handle(job)).hasMessageContaining("构建快照不一致");
        verifyNoInteractions(llm);
    }

    @Test
    void shouldFailClosedWhenEndpointOrTemperatureChangedAfterBuildCreation() {
        QuestionBankBuildMapper buildMapper = mock(QuestionBankBuildMapper.class);
        when(buildMapper.update(isNull(), any(UpdateWrapper.class))).thenReturn(1);
        UserLlmConfigService configService = mock(UserLlmConfigService.class);
        QuestionBankBuildLlm llm = mock(QuestionBankBuildLlm.class);
        AppJobService appJobService = mock(AppJobService.class);
        QuestionBankBuildJobHandler handler = handler(
                buildMapper, mock(QuestionBankBuildCandidateMapper.class), mock(KnowledgeSourceFileMapper.class),
                mock(QuestionBankBuildFileStorage.class), new QuestionBankBuildProperties(), configService, llm,
                appJobService, mock(QuestionBankBuildSupervisionService.class));
        UserLlmRuntimeConfig original = new UserLlmRuntimeConfig(
                3L, 7L, "openai", "test", "https://old.example/v1", "gpt-5", "secret", 0.1);
        QuestionBankBuild build = new QuestionBankBuild();
        build.setId(19L); build.setScope("PRIVATE"); build.setOwnerUserId(7L); build.setLlmConfigId(3L);
        build.setLlmProvider("openai"); build.setLlmModel("gpt-5");
        build.setLlmRuntimeFingerprint(QuestionBankBuildRuntimeSnapshot.fingerprint(original));
        when(buildMapper.selectById(19L)).thenReturn(build);
        when(configService.requireOwnedRuntimeConfig(7L, 3L)).thenReturn(
                new UserLlmRuntimeConfig(3L, 7L, "openai", "test",
                        "https://new.example/v1", "gpt-5", "secret", 0.2));
        AppJob job = new AppJob(); job.setId(107L); job.setBuildId(19L); job.setClaimedBy("worker");
        bindArtifacts(build, new KnowledgeSourceFile(), job);

        assertThatThrownBy(() -> handler.handle(job)).hasMessageContaining("构建快照不一致");
        verifyNoInteractions(llm);
    }

    @Test
    void shouldRequireExplicitRetryBeforeRepeatingUnconfirmedSupervisionCall() {
        QuestionBankBuildMapper buildMapper = mock(QuestionBankBuildMapper.class);
        QuestionBankBuildCandidateMapper candidateMapper = mock(QuestionBankBuildCandidateMapper.class);
        AppJobService appJobService = mock(AppJobService.class);
        QuestionBankBuildSupervisionService supervision = mock(QuestionBankBuildSupervisionService.class);
        QuestionBankBuildProperties properties = new QuestionBankBuildProperties();
        properties.setMaxRepairRounds(0);
        QuestionBankBuild build = runningBuild(40L);
        QuestionBankBuildCandidate candidate = validCandidate(80L, 40L);
        when(buildMapper.selectById(40L)).thenReturn(build);
        when(candidateMapper.selectById(80L)).thenReturn(candidate);
        when(candidateMapper.selectList(any(QueryWrapper.class))).thenReturn(List.of(candidate));
        when(candidateMapper.update(any(QuestionBankBuildCandidate.class), any(UpdateWrapper.class))).thenReturn(1);
        when(appJobService.extendRunningJobLease(eq(301L), eq("worker"), any())).thenReturn(true);
        QuestionBankBuildCheckpoint checkpoint = QuestionBankBuildCheckpoint.parse(null);
        checkpoint.startSupervision(80L, 0);
        AppJob job = new AppJob(); job.setId(301L); job.setClaimedBy("worker"); job.setRetryCount(0);
        QuestionBankBuildSupervisionRunner runner = new QuestionBankBuildSupervisionRunner(
                buildMapper, candidateMapper, appJobService, supervision,
                mock(QuestionBankBuildRepairService.class), mock(QuestionBankBuildCandidateValidator.class), properties);

        assertThatThrownBy(() -> runner.run(build, Map.of(0, "JVM 文档片段"),
                mock(UserLlmRuntimeConfig.class), job, checkpoint))
                .hasMessageContaining("避免重复计费");
        verify(supervision, never()).complete(any());

        job.setRetryCount(1);
        when(supervision.complete(any())).thenAnswer(invocation -> {
            assertThat(JSON.parseObject(build.getCheckpointJson())
                    .getJSONObject("supervision").getIntValue("startedRetryCount")).isEqualTo(1);
            return "pass";
        });
        when(supervision.parse("pass")).thenReturn(new QuestionBankBuildSupervisionResult(
                "AUTO_PASS", 0.95, List.of(), Map.of(), null, "pass"));

        runner.run(build, Map.of(0, "JVM 文档片段"), mock(UserLlmRuntimeConfig.class), job, checkpoint);

        verify(supervision, times(1)).complete(any());
        assertThat(checkpoint.supervision()).isNull();
    }

    @Test
    void shouldRewriteRepairRetryCheckpointBeforeAuthorizedPaidRecall() {
        QuestionBankBuildMapper buildMapper = mock(QuestionBankBuildMapper.class);
        QuestionBankBuildCandidateMapper candidateMapper = mock(QuestionBankBuildCandidateMapper.class);
        AppJobService appJobService = mock(AppJobService.class);
        QuestionBankBuildSupervisionService supervision = mock(QuestionBankBuildSupervisionService.class);
        QuestionBankBuildRepairService repairService = mock(QuestionBankBuildRepairService.class);
        QuestionBankBuildProperties properties = new QuestionBankBuildProperties();
        properties.setMaxRepairRounds(1);
        QuestionBankBuild build = runningBuild(41L); build.setRepairRound(1);
        QuestionBankBuildCandidate candidate = validCandidate(81L, 41L);
        candidate.setMachineReviewStatus("NEEDS_HUMAN"); candidate.setRepairStatus("RUNNING");
        when(buildMapper.selectById(41L)).thenReturn(build);
        when(candidateMapper.selectById(81L)).thenReturn(candidate);
        when(candidateMapper.selectList(any(QueryWrapper.class))).thenReturn(List.of(candidate));
        when(candidateMapper.update(any(QuestionBankBuildCandidate.class), any(UpdateWrapper.class))).thenReturn(1);
        when(appJobService.extendRunningJobLease(eq(302L), eq("worker"), any())).thenReturn(true);
        QuestionBankBuildCheckpoint checkpoint = QuestionBankBuildCheckpoint.parse(null);
        checkpoint.startRepair(81L, 1, 0); checkpoint.persistRepairResponse("bad");
        when(repairService.parse(candidate, "bad")).thenThrow(new IllegalStateException("修复结果不是有效 JSON"));
        when(repairService.complete(any())).thenAnswer(invocation -> {
            assertThat(JSON.parseObject(build.getCheckpointJson())
                    .getJSONObject("repair").getIntValue("startedRetryCount")).isEqualTo(1);
            return "good";
        });
        when(repairService.parse(candidate, "good")).thenReturn(new QuestionBankBuildRepairResult(
                "UPDATE", "JVM 类加载", "jvm", "mid", List.of("JVM"),
                "双亲委派", "混淆加载阶段", List.of("深入追问", "引导追问"), "已修复", "good"));
        stubSupervision(supervision, new QuestionBankBuildSupervisionResult(
                "AUTO_PASS", 0.95, List.of(), Map.of(), null, "pass"));
        AppJob job = new AppJob(); job.setId(302L); job.setClaimedBy("worker"); job.setRetryCount(1);
        QuestionBankBuildSupervisionRunner runner = new QuestionBankBuildSupervisionRunner(
                buildMapper, candidateMapper, appJobService, supervision, repairService,
                mock(QuestionBankBuildCandidateValidator.class), properties);

        runner.run(build, Map.of(0, "JVM 文档片段"), mock(UserLlmRuntimeConfig.class), job, checkpoint);

        verify(repairService, times(1)).complete(any());
        assertThat(checkpoint.repair()).isNull();
    }

    @Test
    void shouldRestrictManualRepairToPayloadCandidateScope() {
        QuestionBankBuildMapper buildMapper = mock(QuestionBankBuildMapper.class);
        QuestionBankBuildCandidateMapper candidateMapper = mock(QuestionBankBuildCandidateMapper.class);
        AppJobService appJobService = mock(AppJobService.class);
        QuestionBankBuildRepairService repairService = mock(QuestionBankBuildRepairService.class);
        QuestionBankBuildProperties properties = new QuestionBankBuildProperties(); properties.setMaxRepairRounds(1);
        QuestionBankBuild build = runningBuild(42L);
        QuestionBankBuildCandidate selected = validCandidate(82L, 42L);
        selected.setMachineReviewStatus("NEEDS_HUMAN"); selected.setRepairStatus("PENDING");
        QuestionBankBuildCandidate outside = validCandidate(83L, 42L);
        outside.setMachineReviewStatus("NEEDS_HUMAN"); outside.setRepairStatus("PENDING");
        when(buildMapper.selectById(42L)).thenReturn(build);
        when(candidateMapper.selectList(any(QueryWrapper.class))).thenReturn(List.of(selected, outside));
        when(candidateMapper.update(any(QuestionBankBuildCandidate.class), any(UpdateWrapper.class))).thenReturn(1);
        when(appJobService.extendRunningJobLease(eq(303L), eq("worker"), any())).thenReturn(true);
        when(repairService.complete(any())).thenReturn("drop");
        when(repairService.parse(selected, "drop")).thenReturn(new QuestionBankBuildRepairResult(
                "DROP", null, null, null, List.of(), null, null, List.of(), "重复", "drop"));
        AppJob job = new AppJob(); job.setId(303L); job.setClaimedBy("worker"); job.setRetryCount(0);
        new QuestionBankBuildSupervisionRunner(buildMapper, candidateMapper, appJobService,
                mock(QuestionBankBuildSupervisionService.class), repairService,
                mock(QuestionBankBuildCandidateValidator.class), properties)
                .run(build, Map.of(0, "JVM 文档片段"), mock(UserLlmRuntimeConfig.class), job,
                        QuestionBankBuildCheckpoint.parse(null), Set.of(82L));

        assertThat(selected.getRepairStatus()).isEqualTo("DROPPED");
        assertThat(outside.getRepairStatus()).isEqualTo("PENDING");
        assertThat(outside.getRepairAttempts()).isZero();
        verify(repairService, times(1)).complete(any());
    }

    @Test
    void shouldRestoreReadyStateWhenManualRepairFails() {
        QuestionBankBuildMapper buildMapper = mock(QuestionBankBuildMapper.class);
        when(buildMapper.update(isNull(), any(UpdateWrapper.class))).thenReturn(1);
        QuestionBankBuildCandidateMapper candidateMapper = mock(QuestionBankBuildCandidateMapper.class);
        when(candidateMapper.update(isNull(), any(UpdateWrapper.class))).thenReturn(2);
        when(candidateMapper.selectCount(any(QueryWrapper.class))).thenReturn(
                42L, 0L, 0L, 38L, 2L, 2L, 3L, 2L);
        UserLlmConfigService configService = mock(UserLlmConfigService.class);
        AppJobService appJobService = mock(AppJobService.class);
        QuestionBankBuildJobHandler handler = handler(buildMapper, candidateMapper,
                mock(KnowledgeSourceFileMapper.class), mock(QuestionBankBuildFileStorage.class),
                new QuestionBankBuildProperties(), configService, mock(QuestionBankBuildLlm.class),
                appJobService, mock(QuestionBankBuildSupervisionService.class));
        UserLlmRuntimeConfig original = new UserLlmRuntimeConfig(
                3L, 7L, "openai", "test", "https://old.example/v1", "gpt-5", "secret", 0.1);
        QuestionBankBuild build = new QuestionBankBuild();
        build.setId(43L); build.setScope("PRIVATE"); build.setOwnerUserId(7L); build.setLlmConfigId(3L);
        build.setStatus("PENDING"); build.setStage("REPAIRING"); build.setProgress(76);
        build.setLlmRuntimeFingerprint(QuestionBankBuildRuntimeSnapshot.fingerprint(original));
        when(buildMapper.selectById(43L)).thenReturn(build);
        when(configService.requireOwnedRuntimeConfig(7L, 3L)).thenReturn(new UserLlmRuntimeConfig(
                3L, 7L, "openai", "test", "https://changed.example/v1", "gpt-5", "secret", 0.1));
        AppJob job = new AppJob(); job.setId(304L); job.setBuildId(43L); job.setClaimedBy("worker");
        job.setPayloadJson("{\"candidateIds\":[82]}");
        bindArtifacts(build, new KnowledgeSourceFile(), job);

        assertThatThrownBy(() -> handler.handle(job)).hasMessageContaining("构建快照不一致");
        assertThat(build.getStatus()).isEqualTo(AppJobService.STATUS_COMPLETED);
        assertThat(build.getStage()).isEqualTo("READY_FOR_FINAL_REVIEW");
        assertThat(build.getProgress()).isEqualTo(100);
        assertThat(build.getNeedsHumanCount()).isEqualTo(2);
        assertThat(build.getRepairedCount()).isEqualTo(3);
        assertThat(build.getRepairFailedCount()).isEqualTo(2);
        ArgumentCaptor<UpdateWrapper<QuestionBankBuildCandidate>> normalized = ArgumentCaptor.forClass(UpdateWrapper.class);
        verify(candidateMapper, times(3)).update(isNull(), normalized.capture());
        assertThat(normalized.getAllValues().stream().map(UpdateWrapper::getSqlSet).toList())
                .anySatisfy(sql -> assertThat(sql).contains("machine_review_status", "repair_status"));
    }

    @Test
    void shouldNotWriteBuildAfterJobTokenCasIsLost() {
        QuestionBankBuildMapper buildMapper = mock(QuestionBankBuildMapper.class);
        UserLlmConfigService configService = mock(UserLlmConfigService.class);
        AppJobService appJobService = mock(AppJobService.class);
        doThrow(new IllegalStateException("lost")).when(appJobService)
                .updateRunningJob(305L, "worker", "PARSING", 0);
        QuestionBankBuildJobHandler handler = handler(buildMapper, mock(QuestionBankBuildCandidateMapper.class),
                mock(KnowledgeSourceFileMapper.class), mock(QuestionBankBuildFileStorage.class),
                new QuestionBankBuildProperties(), configService, mock(QuestionBankBuildLlm.class),
                appJobService, mock(QuestionBankBuildSupervisionService.class));
        QuestionBankBuild build = new QuestionBankBuild();
        build.setId(44L); build.setScope("PRIVATE"); build.setOwnerUserId(7L); build.setLlmConfigId(3L);
        build.setStatus("PENDING"); build.setStage("QUEUED");
        when(buildMapper.selectById(44L)).thenReturn(build);
        AppJob job = new AppJob(); job.setId(305L); job.setBuildId(44L); job.setClaimedBy("worker");
        bindArtifacts(build, new KnowledgeSourceFile(), job);

        assertThatThrownBy(() -> handler.handle(job)).hasMessageContaining("租约已失效");
        verify(buildMapper, never()).update(isNull(), any(UpdateWrapper.class));
        verify(buildMapper, never()).updateById(any());
    }

    @Test
    void shouldNotMarkBuildFailedWhenLeaseIsLostWhileHandlingAnotherFailure() {
        QuestionBankBuildMapper buildMapper = mock(QuestionBankBuildMapper.class);
        when(buildMapper.update(isNull(), any(UpdateWrapper.class))).thenReturn(1);
        UserLlmConfigService configService = mock(UserLlmConfigService.class);
        when(configService.requireOwnedRuntimeConfig(7L, 3L))
                .thenThrow(new IllegalStateException("provider unavailable"));
        AppJobService appJobService = mock(AppJobService.class);
        QuestionBankBuildJobHandler handler = handler(buildMapper, mock(QuestionBankBuildCandidateMapper.class),
                mock(KnowledgeSourceFileMapper.class), mock(QuestionBankBuildFileStorage.class),
                new QuestionBankBuildProperties(), configService, mock(QuestionBankBuildLlm.class),
                appJobService, mock(QuestionBankBuildSupervisionService.class));
        when(appJobService.extendRunningJobLease(eq(308L), eq("worker"), any())).thenReturn(false);
        QuestionBankBuild build = new QuestionBankBuild();
        build.setId(47L); build.setScope("PRIVATE"); build.setOwnerUserId(7L); build.setLlmConfigId(3L);
        build.setStatus("PENDING"); build.setStage("QUEUED");
        when(buildMapper.selectById(47L)).thenReturn(build);
        AppJob job = new AppJob(); job.setId(308L); job.setBuildId(47L); job.setClaimedBy("worker");
        bindArtifacts(build, new KnowledgeSourceFile(), job);

        assertThatThrownBy(() -> handler.handle(job)).hasMessageContaining("租约已失效");
        verify(buildMapper, times(1)).update(isNull(), any(UpdateWrapper.class));
        assertThat(build.getStatus()).isEqualTo(AppJobService.STATUS_RUNNING);
    }

    @Test
    void shouldFinishIdempotentlyFromPersistedCompletionWindow() {
        QuestionBankBuildMapper buildMapper = mock(QuestionBankBuildMapper.class);
        when(buildMapper.update(isNull(), any(UpdateWrapper.class))).thenReturn(1);
        UserLlmConfigService configService = mock(UserLlmConfigService.class);
        QuestionBankBuildJobHandler handler = handler(buildMapper, mock(QuestionBankBuildCandidateMapper.class),
                mock(KnowledgeSourceFileMapper.class), mock(QuestionBankBuildFileStorage.class),
                new QuestionBankBuildProperties(), configService, mock(QuestionBankBuildLlm.class),
                mock(AppJobService.class), mock(QuestionBankBuildSupervisionService.class));
        QuestionBankBuild build = new QuestionBankBuild();
        build.setId(45L); build.setScope("PRIVATE"); build.setOwnerUserId(7L); build.setLlmConfigId(3L);
        build.setStatus("RUNNING"); build.setStage("RESUPERVISING"); build.setChunkCount(2); build.setCompletedChunkCount(2);
        when(buildMapper.selectById(45L)).thenReturn(build);
        AppJob job = new AppJob(); job.setId(306L); job.setBuildId(45L); job.setClaimedBy("worker");
        job.setStage("READY_FOR_FINAL_REVIEW");
        bindArtifacts(build, new KnowledgeSourceFile(), job);

        handler.handle(job);

        assertThat(build.getStatus()).isEqualTo(AppJobService.STATUS_COMPLETED);
        assertThat(job.getResultJson()).contains("\"chunkCount\":2");
        verifyNoInteractions(configService);
    }

    @Test
    void candidateMachineWriteMustBeWhitelistedAndGuardedByPendingHumanReview() {
        QuestionBankBuildMapper buildMapper = mock(QuestionBankBuildMapper.class);
        QuestionBankBuildCandidateMapper candidateMapper = mock(QuestionBankBuildCandidateMapper.class);
        AppJobService appJobService = mock(AppJobService.class);
        QuestionBankBuildSupervisionService supervision = mock(QuestionBankBuildSupervisionService.class);
        QuestionBankBuildProperties properties = new QuestionBankBuildProperties(); properties.setMaxRepairRounds(0);
        QuestionBankBuild build = runningBuild(46L);
        QuestionBankBuildCandidate candidate = validCandidate(84L, 46L);
        QuestionBankBuildCandidate humanDecision = validCandidate(84L, 46L);
        humanDecision.setReviewStatus("REJECTED"); humanDecision.setReviewReason("人工排除");
        when(buildMapper.selectById(46L)).thenReturn(build);
        when(candidateMapper.selectList(any(QueryWrapper.class))).thenReturn(List.of(candidate));
        when(candidateMapper.update(any(QuestionBankBuildCandidate.class), any(UpdateWrapper.class))).thenReturn(0);
        when(candidateMapper.selectById(84L)).thenReturn(humanDecision);
        when(appJobService.extendRunningJobLease(eq(307L), eq("worker"), any())).thenReturn(true);
        stubSupervision(supervision, new QuestionBankBuildSupervisionResult(
                "AUTO_PASS", 0.95, List.of(), Map.of(), null, "pass"));
        AppJob job = new AppJob(); job.setId(307L); job.setClaimedBy("worker"); job.setRetryCount(0);

        new QuestionBankBuildSupervisionRunner(buildMapper, candidateMapper, appJobService, supervision,
                mock(QuestionBankBuildRepairService.class), mock(QuestionBankBuildCandidateValidator.class), properties)
                .run(build, Map.of(0, "JVM 文档片段"), mock(UserLlmRuntimeConfig.class), job,
                        QuestionBankBuildCheckpoint.parse(null));

        ArgumentCaptor<QuestionBankBuildCandidate> update = ArgumentCaptor.forClass(QuestionBankBuildCandidate.class);
        ArgumentCaptor<UpdateWrapper<QuestionBankBuildCandidate>> where = ArgumentCaptor.forClass(UpdateWrapper.class);
        verify(candidateMapper).update(update.capture(), where.capture());
        assertThat(update.getValue().getReviewStatus()).isNull();
        assertThat(update.getValue().getReviewReason()).isNull();
        assertThat(where.getValue().getSqlSegment().toLowerCase()).contains("review_status");
        verify(candidateMapper, never()).updateById(any());
        assertThat(candidate.getReviewStatus()).isEqualTo("REJECTED");
    }

    private QuestionBankBuildJobHandler handler(QuestionBankBuildMapper buildMapper,
                                                QuestionBankBuildCandidateMapper candidateMapper,
                                                KnowledgeSourceFileMapper sourceFileMapper,
                                                QuestionBankBuildFileStorage storage,
                                                QuestionBankBuildProperties properties,
                                                UserLlmConfigService configService,
                                                QuestionBankBuildLlm llm,
                                                AppJobService appJobService,
                                                QuestionBankBuildSupervisionService supervisionService) {
        properties.setMaxRepairRounds(0);
        when(appJobService.extendRunningJobLease(anyLong(), anyString(), any())).thenReturn(true);
        when(candidateMapper.update(any(QuestionBankBuildCandidate.class), any(UpdateWrapper.class))).thenReturn(1);
        QuestionBankBuildCandidateValidator validator = new QuestionBankBuildCandidateValidator();
        return new QuestionBankBuildJobHandler(
                buildMapper, candidateMapper, sourceFileMapper, storage, properties, configService, llm,
                appJobService, supervisionService, new QuestionBankBuildRepairService(llm, validator),
                new QuestionBankBuildDocumentExtractor(), validator);
    }

    private void stubSupervision(QuestionBankBuildSupervisionService service,
                                 QuestionBankBuildSupervisionResult... results) {
        AtomicInteger index = new AtomicInteger();
        when(service.complete(any())).thenReturn("{}");
        when(service.parse(anyString())).thenAnswer(invocation ->
                results[Math.min(index.getAndIncrement(), results.length - 1)]);
    }

    private void bindArtifacts(QuestionBankBuild build, KnowledgeSourceFile source, AppJob job) {
        if (build.getPositionId() == null) build.setPositionId(20L);
        if (build.getKnowledgeBaseId() == null) build.setKnowledgeBaseId(10L);
        if (build.getLlmRuntimeFingerprint() == null
                && build.getLlmProvider() == null && build.getLlmModel() == null) {
            build.setLlmRuntimeFingerprint(QuestionBankBuildRuntimeSnapshot.fingerprint(
                    new UserLlmRuntimeConfig(3L, build.getOwnerUserId(), "test", "test",
                            "http://localhost", "mock", "secret", 0.0)));
        }
        source.setScope("PRIVATE");
        source.setBuildId(build.getId());
        source.setOwnerUserId(build.getOwnerUserId());
        source.setPositionId(build.getPositionId());
        source.setKnowledgeBaseId(build.getKnowledgeBaseId());
        job.setScope("PRIVATE");
        job.setOwnerUserId(build.getOwnerUserId());
        job.setPositionId(build.getPositionId());
        job.setKnowledgeBaseId(build.getKnowledgeBaseId());
    }

    private QuestionBankBuild runningBuild(Long id) {
        QuestionBankBuild build = new QuestionBankBuild();
        build.setId(id); build.setScope("PRIVATE"); build.setStatus("RUNNING");
        build.setCheckpointJson("[]"); build.setRepairRound(0);
        return build;
    }

    private QuestionBankBuildCandidate validCandidate(Long id, Long buildId) {
        QuestionBankBuildCandidate candidate = new QuestionBankBuildCandidate();
        candidate.setId(id); candidate.setBuildId(buildId); candidate.setChunkIndex(0);
        candidate.setSubject("JVM"); candidate.setCategory("jvm"); candidate.setDifficulty("mid");
        candidate.setPrinciples("类加载原则"); candidate.setPitfalls("常见误区");
        candidate.setFollowUpPathsJson("[\"深入追问\",\"引导追问\"]");
        candidate.setSourceRef("notes.md#chunk-0");
        candidate.setSourceEvidenceJson("[{\"quote\":\"JVM 文档片段\"}]");
        candidate.setReviewStatus("PENDING"); candidate.setMachineReviewStatus("PENDING");
        candidate.setRepairStatus("NOT_NEEDED"); candidate.setRepairAttempts(0); candidate.setRepairRound(0);
        return candidate;
    }
}
