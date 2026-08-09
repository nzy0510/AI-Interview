package com.interview.service.questionbank.build;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.interview.dto.questionbank.build.QuestionBankBuildRepairRequest;
import com.interview.dto.questionbank.build.QuestionBankBuildResponse;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class QuestionBankBuildRepairRequestServiceTest {
    @Test
    void shouldCreateBoundedRepairJobAndInvalidateFinalReviewRevision() {
        QuestionBankBuildService buildService = mock(QuestionBankBuildService.class);
        QuestionBankBuildMapper buildMapper = mock(QuestionBankBuildMapper.class);
        QuestionBankBuildCandidateMapper candidateMapper = mock(QuestionBankBuildCandidateMapper.class);
        AppJobMapper jobMapper = mock(AppJobMapper.class);
        AppJobService appJobService = mock(AppJobService.class);
        AppJobRecoveryService recoveryService = mock(AppJobRecoveryService.class);
        QuestionBankBuildResponseAssembler assembler = mock(QuestionBankBuildResponseAssembler.class);
        AtomicBoolean transactionFinished = new AtomicBoolean(false);
        QuestionBankBuildRepairRequestService service = new QuestionBankBuildRepairRequestService(
                buildService, buildMapper, candidateMapper, jobMapper, appJobService,
                recoveryService, assembler, transactionTemplate(transactionFinished));
        QuestionBankBuild build = new QuestionBankBuild();
        build.setId(99L); build.setScope("PRIVATE"); build.setOwnerUserId(7L);
        build.setPositionId(20L); build.setKnowledgeBaseId(10L);
        build.setStatus("COMPLETED"); build.setStage("READY_FOR_FINAL_REVIEW");
        build.setReviewRevision(4L); build.setCheckpointJson("[]");
        QuestionBankBuildCandidate candidate = new QuestionBankBuildCandidate();
        candidate.setId(101L); candidate.setBuildId(99L); candidate.setOwnerUserId(7L);
        candidate.setReviewStatus("PENDING"); candidate.setMachineReviewStatus("NEEDS_HUMAN");
        candidate.setRepairStatus("EXHAUSTED"); candidate.setRepairRound(2);
        when(buildService.requireOwnedBuild(7L, 10L, 99L)).thenReturn(build);
        when(candidateMapper.selectList(any(QueryWrapper.class))).thenReturn(List.of(candidate));
        when(buildMapper.update(any(), any())).thenReturn(1);
        doAnswer(invocation -> {
            AppJob job = invocation.getArgument(0);
            job.setId(601L);
            return job;
        }).when(appJobService).createPendingJob(any(AppJob.class));
        when(assembler.toResponse(eq(build), eq(601L))).thenAnswer(invocation -> {
            QuestionBankBuildResponse response = new QuestionBankBuildResponse();
            response.setBuildId(99L); response.setJobId(601L); response.setStage(build.getStage());
            return response;
        });
        doAnswer(invocation -> {
            assertThat(transactionFinished).isTrue();
            return null;
        }).when(recoveryService).dispatchJob(601L);
        QuestionBankBuildRepairRequest request = new QuestionBankBuildRepairRequest();
        request.setCandidateIds(List.of(101L));
        request.setInstruction("只依据原文收窄主题");
        request.setExpectedReviewRevision(4L);

        QuestionBankBuildResponse response = service.start(7L, 10L, 99L, request);

        assertThat(response.getJobId()).isEqualTo(601L);
        assertThat(candidate.getRepairStatus()).isEqualTo("PENDING");
        assertThat(candidate.getRepairRound()).isZero();
        assertThat(candidate.getRepairInstruction()).isEqualTo("只依据原文收窄主题");
        assertThat(build.getStage()).isEqualTo("REPAIRING");
        assertThat(build.getReviewRevision()).isEqualTo(5L);
        verify(appJobService).createPendingJob(any(AppJob.class));
        verify(recoveryService).dispatchJob(601L);
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
