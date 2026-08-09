package com.interview.service.questionbank.build;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.interview.dto.questionbank.build.QuestionBankBuildFinalizationRequest;
import com.interview.entity.AppJob;
import com.interview.entity.QuestionBankBuild;
import com.interview.entity.QuestionBankBuildCandidate;
import com.interview.mapper.AppJobMapper;
import com.interview.mapper.QuestionBankBuildCandidateMapper;
import com.interview.mapper.QuestionBankBuildMapper;
import com.interview.service.AppJobRecoveryService;
import com.interview.service.AppJobService;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class QuestionBankBuildFinalizationServiceTest {

    @Test
    void shouldCreateOwnerScopedFinalReviewJobForExplicitAutoPassCandidates() {
        QuestionBankBuildService buildService = mock(QuestionBankBuildService.class);
        QuestionBankBuildMapper buildMapper = mock(QuestionBankBuildMapper.class);
        QuestionBankBuildCandidateMapper candidateMapper = mock(QuestionBankBuildCandidateMapper.class);
        AppJobMapper appJobMapper = mock(AppJobMapper.class);
        AppJobService appJobService = mock(AppJobService.class);
        AppJobRecoveryService recoveryService = mock(AppJobRecoveryService.class);
        AtomicBoolean transactionFinished = new AtomicBoolean(false);
        TransactionTemplate transactionTemplate = transactionTemplate(transactionFinished);
        QuestionBankBuildFinalizationService service = new QuestionBankBuildFinalizationService(
                buildService, buildMapper, candidateMapper, appJobMapper, appJobService,
                mock(QuestionBankBuildFinalizationPreparationService.class), transactionTemplate, recoveryService);
        QuestionBankBuild build = readyBuild(4L);
        when(buildService.requireOwnedBuild(7L, 10L, 99L)).thenReturn(build);
        when(buildMapper.update(any(), any())).thenReturn(1);
        QuestionBankBuildCandidate candidate = candidate(101L, "AUTO_PASS", "PENDING");
        when(candidateMapper.selectList(any(QueryWrapper.class))).thenReturn(List.of(candidate));
        doAnswer(invocation -> {
            AppJob job = invocation.getArgument(0);
            job.setId(501L);
            return job;
        }).when(appJobService).createPendingJob(any(AppJob.class));
        doAnswer(invocation -> {
            assertThat(transactionFinished).as("作业只能在事务提交后调度").isTrue();
            return null;
        }).when(recoveryService).dispatchJob(501L);

        QuestionBankBuildFinalizationRequest request = new QuestionBankBuildFinalizationRequest();
        request.setCandidateIds(List.of(101L));
        request.setExpectedReviewRevision(4L);
        var response = service.start(7L, 10L, 99L, request);

        assertThat(response.getJobId()).isEqualTo(501L);
        assertThat(response.getStage()).isEqualTo("FINALIZING");
        assertThat(build.getFinalizedBy()).isEqualTo(7L);
        assertThat(build.getReviewRevision()).isEqualTo(5L);
        verify(appJobService).createPendingJob(any(AppJob.class));
        verify(recoveryService).dispatchJob(501L);
    }

    @Test
    void shouldPublishSelectedAutoPassWhileUnresolvedCandidatesRemainUnselected() {
        QuestionBankBuildService buildService = mock(QuestionBankBuildService.class);
        QuestionBankBuildMapper buildMapper = mock(QuestionBankBuildMapper.class);
        QuestionBankBuildCandidateMapper candidateMapper = mock(QuestionBankBuildCandidateMapper.class);
        AppJobService appJobService = mock(AppJobService.class);
        AppJobRecoveryService recoveryService = mock(AppJobRecoveryService.class);
        QuestionBankBuildFinalizationService service = new QuestionBankBuildFinalizationService(
                buildService,
                buildMapper,
                candidateMapper,
                mock(AppJobMapper.class),
                appJobService,
                mock(QuestionBankBuildFinalizationPreparationService.class),
                transactionTemplate(new AtomicBoolean()),
                recoveryService);
        when(buildService.requireOwnedBuild(7L, 10L, 99L)).thenReturn(readyBuild(4L));
        when(buildMapper.update(any(), any())).thenReturn(1);
        when(candidateMapper.selectList(any(QueryWrapper.class))).thenReturn(List.of(
                candidate(101L, "AUTO_PASS", "PENDING"),
                candidate(102L, "NEEDS_HUMAN", "PENDING")
        ));
        doAnswer(invocation -> {
            AppJob job = invocation.getArgument(0);
            job.setId(502L);
            return job;
        }).when(appJobService).createPendingJob(any(AppJob.class));
        QuestionBankBuildFinalizationRequest request = new QuestionBankBuildFinalizationRequest();
        request.setCandidateIds(List.of(101L));
        request.setExpectedReviewRevision(4L);

        assertThat(service.start(7L, 10L, 99L, request).getJobId()).isEqualTo(502L);
        verify(appJobService).createPendingJob(any(AppJob.class));
    }

    @Test
    void shouldFailClosedForUnknownMachineReviewStatus() {
        QuestionBankBuildService buildService = mock(QuestionBankBuildService.class);
        QuestionBankBuildCandidateMapper candidateMapper = mock(QuestionBankBuildCandidateMapper.class);
        QuestionBankBuildFinalizationService service = new QuestionBankBuildFinalizationService(
                buildService,
                mock(QuestionBankBuildMapper.class),
                candidateMapper,
                mock(AppJobMapper.class),
                mock(AppJobService.class),
                mock(QuestionBankBuildFinalizationPreparationService.class),
                transactionTemplate(new AtomicBoolean()),
                mock(AppJobRecoveryService.class));
        when(buildService.requireOwnedBuild(7L, 10L, 99L)).thenReturn(readyBuild(4L));
        when(candidateMapper.selectList(any(QueryWrapper.class))).thenReturn(List.of(
                candidate(101L, "AUTO_PASS", "PENDING"),
                candidate(102L, "UNRECOGNIZED", "PENDING")
        ));
        QuestionBankBuildFinalizationRequest request = new QuestionBankBuildFinalizationRequest();
        request.setCandidateIds(List.of(102L));
        request.setExpectedReviewRevision(4L);

        assertThatThrownBy(() -> service.start(7L, 10L, 99L, request))
                .hasMessageContaining("尚未通过机器监督");
    }

    @Test
    void shouldNeverPublishCandidateExplicitlyRejectedByHumanEvenWhenMachineAutoPassed() {
        QuestionBankBuildService buildService = mock(QuestionBankBuildService.class);
        QuestionBankBuildCandidateMapper candidateMapper = mock(QuestionBankBuildCandidateMapper.class);
        QuestionBankBuildFinalizationService service = new QuestionBankBuildFinalizationService(
                buildService,
                mock(QuestionBankBuildMapper.class),
                candidateMapper,
                mock(AppJobMapper.class),
                mock(AppJobService.class),
                mock(QuestionBankBuildFinalizationPreparationService.class),
                transactionTemplate(new AtomicBoolean()),
                mock(AppJobRecoveryService.class));
        when(buildService.requireOwnedBuild(7L, 10L, 99L)).thenReturn(readyBuild(4L));
        when(candidateMapper.selectList(any(QueryWrapper.class))).thenReturn(List.of(
                candidate(101L, "AUTO_PASS", "REJECTED")
        ));
        QuestionBankBuildFinalizationRequest request = new QuestionBankBuildFinalizationRequest();
        request.setCandidateIds(List.of(101L));
        request.setExpectedReviewRevision(4L);

        assertThatThrownBy(() -> service.start(7L, 10L, 99L, request))
                .hasMessageContaining("人工拒绝");
    }

    @Test
    void shouldReturnExistingJobOnlyForTheSameCandidateSelection() {
        QuestionBankBuildService buildService = mock(QuestionBankBuildService.class);
        AppJobMapper appJobMapper = mock(AppJobMapper.class);
        QuestionBankBuildFinalizationService service = new QuestionBankBuildFinalizationService(
                buildService,
                mock(QuestionBankBuildMapper.class),
                mock(QuestionBankBuildCandidateMapper.class),
                appJobMapper,
                mock(AppJobService.class),
                mock(QuestionBankBuildFinalizationPreparationService.class),
                transactionTemplate(new AtomicBoolean()),
                mock(AppJobRecoveryService.class));
        QuestionBankBuild build = readyBuild(5L);
        build.setStatus("PENDING");
        build.setStage("FINALIZING");
        when(buildService.requireOwnedBuild(7L, 10L, 99L)).thenReturn(build);
        AppJob existing = new AppJob();
        existing.setId(501L);
        existing.setJobType(QuestionBankBuildFinalizationService.JOB_TYPE);
        existing.setScope("PRIVATE");
        existing.setBuildId(99L);
        existing.setOwnerUserId(7L);
        existing.setPositionId(20L);
        existing.setKnowledgeBaseId(10L);
        existing.setPayloadJson("{\"candidateIds\":[101,102]}");
        when(appJobMapper.selectOne(any(QueryWrapper.class))).thenReturn(existing);
        QuestionBankBuildFinalizationRequest same = new QuestionBankBuildFinalizationRequest();
        same.setCandidateIds(List.of(101L, 102L));
        same.setExpectedReviewRevision(4L);

        assertThat(service.start(7L, 10L, 99L, same).getJobId()).isEqualTo(501L);

        QuestionBankBuildFinalizationRequest changed = new QuestionBankBuildFinalizationRequest();
        changed.setCandidateIds(List.of(101L));
        changed.setExpectedReviewRevision(4L);
        assertThatThrownBy(() -> service.start(7L, 10L, 99L, changed))
                .hasMessageContaining("已提交任务不一致");
    }

    private QuestionBankBuild readyBuild(Long reviewRevision) {
        QuestionBankBuild build = new QuestionBankBuild();
        build.setId(99L);
        build.setScope("PRIVATE");
        build.setOwnerUserId(7L);
        build.setPositionId(20L);
        build.setKnowledgeBaseId(10L);
        build.setStatus("COMPLETED");
        build.setStage("READY_FOR_FINAL_REVIEW");
        build.setReviewRevision(reviewRevision);
        return build;
    }

    private QuestionBankBuildCandidate candidate(Long id, String machineStatus, String reviewStatus) {
        QuestionBankBuildCandidate candidate = new QuestionBankBuildCandidate();
        candidate.setId(id);
        candidate.setBuildId(99L);
        candidate.setOwnerUserId(7L);
        candidate.setMachineReviewStatus(machineStatus);
        candidate.setReviewStatus(reviewStatus);
        return candidate;
    }

    @SuppressWarnings("unchecked")
    private TransactionTemplate transactionTemplate(AtomicBoolean transactionFinished) {
        TransactionTemplate template = mock(TransactionTemplate.class);
        when(template.execute(any())).thenAnswer(invocation -> {
            TransactionCallback<Object> callback = invocation.getArgument(0);
            Object result = callback.doInTransaction(mock(TransactionStatus.class));
            transactionFinished.set(true);
            return result;
        });
        return template;
    }
}
