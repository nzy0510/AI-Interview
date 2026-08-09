package com.interview.service.questionbank.build;

import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.interview.config.QuestionBankBuildProperties;
import com.interview.dto.questionbank.build.QuestionBankBuildResponse;
import com.interview.entity.KnowledgeBase;
import com.interview.entity.QuestionBankBuild;
import com.interview.mapper.AppJobMapper;
import com.interview.mapper.KnowledgeSourceFileMapper;
import com.interview.mapper.QuestionBankBuildCandidateMapper;
import com.interview.mapper.QuestionBankBuildMapper;
import com.interview.service.UserLlmConfigService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class QuestionBankBuildServiceAccessTest {
    @Test
    void shouldRejectBuildAccessForForeignPrivateOwner() {
        QuestionBankBuildAccessService accessService = mock(QuestionBankBuildAccessService.class);
        when(accessService.requireBuildTarget(7L, 10L)).thenThrow(new RuntimeException("无权访问知识库"));
        QuestionBankBuildService service = service(accessService, mock(QuestionBankBuildMapper.class));

        assertThatThrownBy(() -> service.detail(7L, 10L, 99L)).hasMessageContaining("无权访问知识库");
    }

    @Test
    void shouldListOnlyTheInitiatingAdminsPrivateBuildArtifactsForAPublicTarget() {
        QuestionBankBuildAccessService accessService = mock(QuestionBankBuildAccessService.class);
        QuestionBankBuildMapper buildMapper = mock(QuestionBankBuildMapper.class);
        QuestionBankBuildResponseAssembler assembler = mock(QuestionBankBuildResponseAssembler.class);
        KnowledgeBase publicBase = new KnowledgeBase();
        publicBase.setId(10L); publicBase.setScope("PUBLIC"); publicBase.setPositionId(20L); publicBase.setStatus("ACTIVE");
        when(accessService.requireBuildTarget(7L, 10L)).thenReturn(publicBase);
        QuestionBankBuild build = new QuestionBankBuild();
        build.setId(99L); build.setScope("PRIVATE"); build.setOwnerUserId(7L); build.setKnowledgeBaseId(10L);
        when(buildMapper.selectList(any())).thenReturn(List.of(build));
        when(assembler.toResponse(build, null)).thenReturn(new QuestionBankBuildResponse());
        QuestionBankBuildService service = service(accessService, buildMapper, assembler);

        assertThat(service.list(7L, 10L)).hasSize(1);
        verify(accessService).requireBuildTarget(7L, 10L);
    }

    @Test
    void shouldRejectDeletingRunningBuildBeforeAnyDataIsRemoved() {
        QuestionBankBuildMapper buildMapper = mock(QuestionBankBuildMapper.class);
        KnowledgeSourceFileMapper sourceFileMapper = mock(KnowledgeSourceFileMapper.class);
        QuestionBankBuildCandidateMapper candidateMapper = mock(QuestionBankBuildCandidateMapper.class);
        AppJobMapper appJobMapper = mock(AppJobMapper.class);
        KnowledgeBase kb = new KnowledgeBase();
        kb.setId(10L); kb.setScope("PRIVATE"); kb.setOwnerUserId(7L); kb.setPositionId(20L); kb.setStatus("ACTIVE");
        QuestionBankBuildAccessService accessService = mock(QuestionBankBuildAccessService.class);
        QuestionBankBuild build = new QuestionBankBuild();
        build.setId(99L); build.setScope("PRIVATE"); build.setOwnerUserId(7L); build.setKnowledgeBaseId(10L); build.setStatus("RUNNING");
        when(accessService.requireBuildTarget(7L, 10L)).thenReturn(kb);
        when(buildMapper.selectById(99L)).thenReturn(build);
        QuestionBankBuildService service = new QuestionBankBuildService(
                sourceFileMapper, buildMapper, candidateMapper, appJobMapper,
                mock(UserLlmConfigService.class), new QuestionBankBuildProperties(),
                mock(QuestionBankBuildInputService.class), mock(QuestionBankBuildFileStorage.class),
                accessService, mock(QuestionBankBuildResponseAssembler.class),
                mock(QuestionBankBuildCreationService.class));

        assertThatThrownBy(() -> service.delete(7L, 10L, 99L))
                .hasMessageContaining("正在执行");
        verifyNoInteractions(sourceFileMapper, candidateMapper, appJobMapper);
        verify(buildMapper, never()).deleteById(99L);
    }

    @Test
    void shouldKeepPublishedBuildsAsAuditableSourceRecords() {
        QuestionBankBuildAccessService accessService = mock(QuestionBankBuildAccessService.class);
        QuestionBankBuildMapper buildMapper = mock(QuestionBankBuildMapper.class);
        when(accessService.requireBuildTarget(7L, 10L)).thenReturn(new KnowledgeBase());
        QuestionBankBuild build = new QuestionBankBuild();
        build.setId(99L); build.setScope("PRIVATE"); build.setOwnerUserId(7L);
        build.setKnowledgeBaseId(10L); build.setStatus("COMPLETED");
        build.setFinalizationStatus("COMPLETED"); build.setFinalAtomIdsJson("[\"atom-1\"]");
        when(buildMapper.selectById(99L)).thenReturn(build);
        QuestionBankBuildService service = service(accessService, buildMapper);

        assertThatThrownBy(() -> service.delete(7L, 10L, 99L))
                .hasMessageContaining("保留来源记录");
        verify(buildMapper, never()).update(isNull(), any(UpdateWrapper.class));
        verify(buildMapper, never()).deleteById(anyLong());
    }

    @Test
    void shouldClaimTerminalBuildBeforeDeleting() throws Exception {
        QuestionBankBuildMapper buildMapper = mock(QuestionBankBuildMapper.class);
        KnowledgeSourceFileMapper sourceFileMapper = mock(KnowledgeSourceFileMapper.class);
        QuestionBankBuildCandidateMapper candidateMapper = mock(QuestionBankBuildCandidateMapper.class);
        AppJobMapper appJobMapper = mock(AppJobMapper.class);
        QuestionBankBuildFileStorage storage = mock(QuestionBankBuildFileStorage.class);
        KnowledgeBase kb = new KnowledgeBase(); kb.setId(10L); kb.setScope("PRIVATE"); kb.setOwnerUserId(7L); kb.setPositionId(20L); kb.setStatus("ACTIVE");
        QuestionBankBuildAccessService accessService = mock(QuestionBankBuildAccessService.class);
        QuestionBankBuild build = new QuestionBankBuild(); build.setId(99L); build.setScope("PRIVATE"); build.setOwnerUserId(7L); build.setKnowledgeBaseId(10L); build.setStatus("FAILED");
        when(accessService.requireBuildTarget(7L, 10L)).thenReturn(kb);
        when(buildMapper.selectById(99L)).thenReturn(build);
        when(buildMapper.update(isNull(), any(UpdateWrapper.class))).thenReturn(1);
        QuestionBankBuildService service = new QuestionBankBuildService(
                sourceFileMapper, buildMapper, candidateMapper, appJobMapper,
                mock(UserLlmConfigService.class), new QuestionBankBuildProperties(),
                mock(QuestionBankBuildInputService.class), storage, accessService,
                mock(QuestionBankBuildResponseAssembler.class), mock(QuestionBankBuildCreationService.class));

        service.delete(7L, 10L, 99L);

        verify(buildMapper).update(isNull(), any(UpdateWrapper.class));
        verify(buildMapper).deleteById(99L);
        verify(storage).deleteBuild(99L);
    }

    private QuestionBankBuildService service(QuestionBankBuildAccessService accessService,
                                             QuestionBankBuildMapper buildMapper) {
        return service(accessService, buildMapper, mock(QuestionBankBuildResponseAssembler.class));
    }

    private QuestionBankBuildService service(QuestionBankBuildAccessService accessService,
                                             QuestionBankBuildMapper buildMapper,
                                             QuestionBankBuildResponseAssembler assembler) {
        return new QuestionBankBuildService(mock(KnowledgeSourceFileMapper.class), buildMapper,
                mock(QuestionBankBuildCandidateMapper.class), mock(AppJobMapper.class),
                mock(UserLlmConfigService.class), new QuestionBankBuildProperties(),
                mock(QuestionBankBuildInputService.class), mock(QuestionBankBuildFileStorage.class),
                accessService, assembler, mock(QuestionBankBuildCreationService.class));
    }

}
