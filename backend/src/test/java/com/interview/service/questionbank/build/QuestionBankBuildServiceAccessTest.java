package com.interview.service.questionbank.build;

import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.interview.config.QuestionBankAccessProperties;
import com.interview.config.QuestionBankBuildProperties;
import com.interview.entity.InterviewPosition;
import com.interview.entity.KnowledgeBase;
import com.interview.entity.QuestionBankBuild;
import com.interview.mapper.AppJobMapper;
import com.interview.mapper.InterviewPositionMapper;
import com.interview.mapper.KnowledgeBaseMapper;
import com.interview.mapper.KnowledgeSourceFileMapper;
import com.interview.mapper.QuestionBankBuildCandidateMapper;
import com.interview.mapper.QuestionBankBuildMapper;
import com.interview.service.UserLlmConfigService;
import com.interview.service.questionbank.KnowledgeWorkspaceService;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class QuestionBankBuildServiceAccessTest {
    @Test
    void shouldRejectBuildAccessForForeignPrivateOwner() {
        KnowledgeBaseMapper knowledgeBaseMapper = mock(KnowledgeBaseMapper.class);
        KnowledgeBase foreign = new KnowledgeBase(); foreign.setId(10L); foreign.setScope("PRIVATE"); foreign.setOwnerUserId(8L); foreign.setPositionId(20L); foreign.setStatus("ACTIVE");
        when(knowledgeBaseMapper.selectById(10L)).thenReturn(foreign);
        QuestionBankBuildService service = service(knowledgeBaseMapper);

        assertThatThrownBy(() -> service.detail(7L, 10L, 99L)).hasMessageContaining("无权访问知识库");
    }

    @Test
    void shouldRejectPublicKnowledgeBaseEvenForAdminIdentity() {
        KnowledgeBaseMapper knowledgeBaseMapper = mock(KnowledgeBaseMapper.class);
        KnowledgeBase publicBase = new KnowledgeBase(); publicBase.setId(10L); publicBase.setScope("PUBLIC"); publicBase.setPositionId(20L); publicBase.setStatus("ACTIVE");
        when(knowledgeBaseMapper.selectById(10L)).thenReturn(publicBase);
        QuestionBankBuildService service = service(knowledgeBaseMapper);

        assertThatThrownBy(() -> service.delete(1L, 10L, 99L)).hasMessageContaining("无权访问知识库");
    }

    @Test
    void shouldRejectDeletingRunningBuildBeforeAnyDataIsRemoved() {
        KnowledgeBaseMapper knowledgeBaseMapper = mock(KnowledgeBaseMapper.class);
        InterviewPositionMapper positionMapper = mock(InterviewPositionMapper.class);
        QuestionBankBuildMapper buildMapper = mock(QuestionBankBuildMapper.class);
        KnowledgeSourceFileMapper sourceFileMapper = mock(KnowledgeSourceFileMapper.class);
        QuestionBankBuildCandidateMapper candidateMapper = mock(QuestionBankBuildCandidateMapper.class);
        AppJobMapper appJobMapper = mock(AppJobMapper.class);
        KnowledgeBase kb = new KnowledgeBase();
        kb.setId(10L); kb.setScope("PRIVATE"); kb.setOwnerUserId(7L); kb.setPositionId(20L); kb.setStatus("ACTIVE");
        InterviewPosition position = new InterviewPosition();
        position.setId(20L); position.setScope("PRIVATE"); position.setOwnerUserId(7L); position.setStatus("ACTIVE");
        QuestionBankBuild build = new QuestionBankBuild();
        build.setId(99L); build.setScope("PRIVATE"); build.setOwnerUserId(7L); build.setKnowledgeBaseId(10L); build.setStatus("RUNNING");
        when(knowledgeBaseMapper.selectById(10L)).thenReturn(kb);
        when(positionMapper.selectById(20L)).thenReturn(position);
        when(buildMapper.selectById(99L)).thenReturn(build);
        QuestionBankAccessProperties access = new QuestionBankAccessProperties(); access.setUserMaintenanceEnabled(true);
        QuestionBankBuildService service = new QuestionBankBuildService(
                knowledgeBaseMapper, positionMapper, sourceFileMapper, buildMapper, candidateMapper, appJobMapper,
                mock(UserLlmConfigService.class), new QuestionBankBuildProperties(), access,
                mock(QuestionBankBuildInputService.class), mock(QuestionBankBuildFileStorage.class),
                mock(KnowledgeWorkspaceService.class), mock(QuestionBankBuildResponseAssembler.class),
                mock(QuestionBankBuildCreationService.class));

        assertThatThrownBy(() -> service.delete(7L, 10L, 99L))
                .hasMessageContaining("正在执行");
        verifyNoInteractions(sourceFileMapper, candidateMapper, appJobMapper);
        verify(buildMapper, never()).deleteById(99L);
    }

    @Test
    void shouldClaimTerminalBuildBeforeDeleting() throws Exception {
        KnowledgeBaseMapper knowledgeBaseMapper = mock(KnowledgeBaseMapper.class);
        InterviewPositionMapper positionMapper = mock(InterviewPositionMapper.class);
        QuestionBankBuildMapper buildMapper = mock(QuestionBankBuildMapper.class);
        KnowledgeSourceFileMapper sourceFileMapper = mock(KnowledgeSourceFileMapper.class);
        QuestionBankBuildCandidateMapper candidateMapper = mock(QuestionBankBuildCandidateMapper.class);
        AppJobMapper appJobMapper = mock(AppJobMapper.class);
        QuestionBankBuildFileStorage storage = mock(QuestionBankBuildFileStorage.class);
        KnowledgeBase kb = new KnowledgeBase(); kb.setId(10L); kb.setScope("PRIVATE"); kb.setOwnerUserId(7L); kb.setPositionId(20L); kb.setStatus("ACTIVE");
        InterviewPosition position = new InterviewPosition(); position.setId(20L); position.setScope("PRIVATE"); position.setOwnerUserId(7L); position.setStatus("ACTIVE");
        QuestionBankBuild build = new QuestionBankBuild(); build.setId(99L); build.setScope("PRIVATE"); build.setOwnerUserId(7L); build.setKnowledgeBaseId(10L); build.setStatus("FAILED");
        when(knowledgeBaseMapper.selectById(10L)).thenReturn(kb);
        when(positionMapper.selectById(20L)).thenReturn(position);
        when(buildMapper.selectById(99L)).thenReturn(build);
        when(buildMapper.update(isNull(), any(UpdateWrapper.class))).thenReturn(1);
        QuestionBankAccessProperties access = new QuestionBankAccessProperties(); access.setUserMaintenanceEnabled(true);
        QuestionBankBuildService service = new QuestionBankBuildService(
                knowledgeBaseMapper, positionMapper, sourceFileMapper, buildMapper, candidateMapper, appJobMapper,
                mock(UserLlmConfigService.class), new QuestionBankBuildProperties(), access,
                mock(QuestionBankBuildInputService.class), storage, mock(KnowledgeWorkspaceService.class),
                mock(QuestionBankBuildResponseAssembler.class), mock(QuestionBankBuildCreationService.class));

        service.delete(7L, 10L, 99L);

        verify(buildMapper).update(isNull(), any(UpdateWrapper.class));
        verify(buildMapper).deleteById(99L);
        verify(storage).deleteBuild(99L);
    }

    private QuestionBankBuildService service(KnowledgeBaseMapper knowledgeBaseMapper) {
        QuestionBankAccessProperties access = new QuestionBankAccessProperties(); access.setUserMaintenanceEnabled(true);
        return new QuestionBankBuildService(knowledgeBaseMapper, mock(InterviewPositionMapper.class), mock(KnowledgeSourceFileMapper.class),
                mock(QuestionBankBuildMapper.class), mock(QuestionBankBuildCandidateMapper.class), mock(AppJobMapper.class),
                mock(UserLlmConfigService.class), new QuestionBankBuildProperties(), access, mock(QuestionBankBuildInputService.class), mock(QuestionBankBuildFileStorage.class),
                mock(KnowledgeWorkspaceService.class), mock(QuestionBankBuildResponseAssembler.class), mock(QuestionBankBuildCreationService.class));
    }

}
