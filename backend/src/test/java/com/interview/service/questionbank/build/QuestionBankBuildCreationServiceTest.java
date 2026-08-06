package com.interview.service.questionbank.build;

import com.interview.dto.questionbank.build.QuestionBankBuildResponse;
import com.interview.entity.AppJob;
import com.interview.entity.KnowledgeBase;
import com.interview.entity.QuestionBankBuild;
import com.interview.mapper.AppJobMapper;
import com.interview.mapper.KnowledgeSourceFileMapper;
import com.interview.mapper.QuestionBankBuildMapper;
import com.interview.service.AppJobRecoveryService;
import com.interview.service.AppJobService;
import com.interview.service.UserLlmRuntimeConfig;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class QuestionBankBuildCreationServiceTest {
    @Test
    void shouldResolveDuplicateRaceOutsideRolledBackTransaction() {
        AppJobMapper jobMapper = mock(AppJobMapper.class);
        QuestionBankBuildMapper buildMapper = mock(QuestionBankBuildMapper.class);
        TransactionTemplate transactionTemplate = mock(TransactionTemplate.class);
        QuestionBankBuildResponseAssembler assembler = mock(QuestionBankBuildResponseAssembler.class);
        AppJob duplicate = new AppJob(); duplicate.setId(8L); duplicate.setBuildId(9L);
        QuestionBankBuild build = new QuestionBankBuild(); build.setId(9L);
        QuestionBankBuildResponse expected = new QuestionBankBuildResponse(); expected.setBuildId(9L); expected.setJobId(8L);
        when(jobMapper.selectOne(any())).thenReturn(null, duplicate);
        when(transactionTemplate.execute(any())).thenThrow(new DuplicateKeyException("duplicate"));
        when(buildMapper.selectById(9L)).thenReturn(build);
        when(assembler.toResponse(build, 8L)).thenReturn(expected);
        QuestionBankBuildCreationService service = service(
                jobMapper, buildMapper, transactionTemplate, assembler, mock(AppJobRecoveryService.class));

        QuestionBankBuildResponse actual = service.create(
                7L, knowledgeBase(), runtime(), List.of(), List.of("java"), 1);

        assertThat(actual).isSameAs(expected);
    }

    @Test
    void shouldKeepCommittedPendingJobWhenImmediateDispatchFails() {
        TransactionTemplate transactionTemplate = mock(TransactionTemplate.class);
        AppJobRecoveryService recovery = mock(AppJobRecoveryService.class);
        QuestionBankBuildResponse expected = new QuestionBankBuildResponse(); expected.setBuildId(9L); expected.setJobId(8L);
        when(transactionTemplate.execute(any())).thenReturn(expected);
        doThrow(new IllegalStateException("executor rejected")).when(recovery).dispatchJob(8L);
        QuestionBankBuildCreationService service = service(
                mock(AppJobMapper.class), mock(QuestionBankBuildMapper.class),
                transactionTemplate, mock(QuestionBankBuildResponseAssembler.class), recovery);

        QuestionBankBuildResponse actual = service.create(
                7L, knowledgeBase(), runtime(), List.of(), List.of("java"), 1);

        assertThat(actual).isSameAs(expected);
        verify(recovery).dispatchJob(8L);
    }

    private QuestionBankBuildCreationService service(AppJobMapper jobMapper,
                                                     QuestionBankBuildMapper buildMapper,
                                                     TransactionTemplate transactionTemplate,
                                                     QuestionBankBuildResponseAssembler assembler,
                                                     AppJobRecoveryService recovery) {
        return new QuestionBankBuildCreationService(
                jobMapper, buildMapper, mock(KnowledgeSourceFileMapper.class),
                mock(AppJobService.class), transactionTemplate,
                new com.interview.config.QuestionBankBuildProperties(),
                mock(QuestionBankBuildFileStorage.class), assembler, recovery);
    }

    private KnowledgeBase knowledgeBase() {
        KnowledgeBase kb = new KnowledgeBase();
        kb.setId(10L); kb.setPositionId(20L);
        return kb;
    }

    private UserLlmRuntimeConfig runtime() {
        return new UserLlmRuntimeConfig(
                3L, 7L, "test", "test", "http://localhost", "mock", "secret", 0.0);
    }
}
