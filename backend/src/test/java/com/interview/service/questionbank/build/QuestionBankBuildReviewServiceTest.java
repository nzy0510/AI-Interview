package com.interview.service.questionbank.build;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.interview.config.QuestionBankAccessProperties;
import com.interview.config.QuestionBankBuildProperties;
import com.interview.dto.questionbank.QuestionBankImportRequest;
import com.interview.dto.questionbank.build.QuestionBankBuildCandidateResponse;
import com.interview.dto.questionbank.build.QuestionBankBuildCandidateReviewRequest;
import com.interview.entity.InterviewPosition;
import com.interview.entity.KnowledgeBase;
import com.interview.entity.QuestionBankBuild;
import com.interview.entity.QuestionBankBuildCandidate;
import com.interview.mapper.AppJobMapper;
import com.interview.mapper.InterviewPositionMapper;
import com.interview.mapper.KnowledgeBaseMapper;
import com.interview.mapper.KnowledgeSourceFileMapper;
import com.interview.mapper.QuestionBankBuildCandidateMapper;
import com.interview.mapper.QuestionBankBuildMapper;
import com.interview.service.UserLlmConfigService;
import com.interview.service.questionbank.KnowledgeWorkspaceService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class QuestionBankBuildReviewServiceTest {
    @Test
    void shouldAcceptEditedCandidateAndIncludeOnlyAcceptedInPackage() {
        KnowledgeBaseMapper kbMapper = mock(KnowledgeBaseMapper.class);
        InterviewPositionMapper positionMapper = mock(InterviewPositionMapper.class);
        QuestionBankBuildMapper buildMapper = mock(QuestionBankBuildMapper.class);
        QuestionBankBuildCandidateMapper candidateMapper = mock(QuestionBankBuildCandidateMapper.class);
        QuestionBankBuildResponseAssembler assembler = mock(QuestionBankBuildResponseAssembler.class);
        KnowledgeBase kb = privateKb(); InterviewPosition position = privatePosition();
        when(kbMapper.selectById(10L)).thenReturn(kb); when(positionMapper.selectById(20L)).thenReturn(position);
        QuestionBankBuild build = new QuestionBankBuild(); build.setId(99L); build.setScope("PRIVATE"); build.setOwnerUserId(7L); build.setKnowledgeBaseId(10L); build.setPositionId(20L); build.setStatus("COMPLETED"); build.setCategoriesJson("[\"java\"]");
        when(buildMapper.selectById(99L)).thenReturn(build);
        QuestionBankBuildCandidate candidate = candidate();
        when(candidateMapper.selectOne(any(QueryWrapper.class))).thenReturn(candidate);
        when(candidateMapper.selectList(any(QueryWrapper.class))).thenReturn(List.of(candidate));
        when(assembler.toCandidateResponse(candidate)).thenReturn(new QuestionBankBuildCandidateResponse());
        QuestionBankBuildService service = newService(kbMapper, positionMapper, buildMapper, candidateMapper, assembler, mock(KnowledgeWorkspaceService.class));

        QuestionBankBuildCandidateReviewRequest review = new QuestionBankBuildCandidateReviewRequest(); review.setAction("ACCEPT");
        QuestionBankBuildCandidateResponse result = service.review(7L, 10L, 99L, 1L, review);

        assertThat(candidate.getReviewStatus()).isEqualTo("ACCEPTED"); assertThat(result).isNotNull();
        verify(candidateMapper).updateById(candidate);
    }

    @Test
    void shouldImportOnlyAcceptedCandidatesAsDraft() {
        KnowledgeBaseMapper kbMapper = mock(KnowledgeBaseMapper.class); InterviewPositionMapper positionMapper = mock(InterviewPositionMapper.class); QuestionBankBuildMapper buildMapper = mock(QuestionBankBuildMapper.class); QuestionBankBuildCandidateMapper candidateMapper = mock(QuestionBankBuildCandidateMapper.class); QuestionBankBuildResponseAssembler assembler = mock(QuestionBankBuildResponseAssembler.class); KnowledgeWorkspaceService workspace = mock(KnowledgeWorkspaceService.class);
        when(kbMapper.selectById(10L)).thenReturn(privateKb()); when(positionMapper.selectById(20L)).thenReturn(privatePosition()); QuestionBankBuild build = new QuestionBankBuild(); build.setId(99L); build.setScope("PRIVATE"); build.setOwnerUserId(7L); build.setKnowledgeBaseId(10L); build.setPositionId(20L); build.setStatus("COMPLETED"); build.setCategoriesJson("[\"java\"]"); when(buildMapper.selectById(99L)).thenReturn(build);
        QuestionBankBuildCandidate accepted = candidate(); when(candidateMapper.selectList(any(QueryWrapper.class))).thenReturn(List.of(accepted)); when(assembler.firstCategory(build)).thenReturn("java"); com.interview.dto.questionbank.KnowledgeAtomPayload payload = new com.interview.dto.questionbank.KnowledgeAtomPayload(); payload.setId("stable-1"); payload.setSubject("JVM"); when(assembler.toPayload(accepted)).thenReturn(payload); when(workspace.importPackage(eq(7L), eq(10L), any(QuestionBankImportRequest.class))).thenReturn(null);
        QuestionBankBuildService service = newService(kbMapper, positionMapper, buildMapper, candidateMapper, assembler, workspace);

        QuestionBankImportRequest request = service.packageFor(7L, 10L, 99L);

        assertThat(request.getMode()).isEqualTo("DRAFT"); assertThat(request.getAtoms()).singleElement().extracting("id").isEqualTo("stable-1");
    }

    @Test
    void shouldAllowViewingButRejectReviewBeforeBuildCompletes() {
        KnowledgeBaseMapper kbMapper = mock(KnowledgeBaseMapper.class);
        InterviewPositionMapper positionMapper = mock(InterviewPositionMapper.class);
        QuestionBankBuildMapper buildMapper = mock(QuestionBankBuildMapper.class);
        QuestionBankBuildCandidateMapper candidateMapper = mock(QuestionBankBuildCandidateMapper.class);
        QuestionBankBuildResponseAssembler assembler = mock(QuestionBankBuildResponseAssembler.class);
        when(kbMapper.selectById(10L)).thenReturn(privateKb());
        when(positionMapper.selectById(20L)).thenReturn(privatePosition());
        QuestionBankBuild build = new QuestionBankBuild();
        build.setId(99L); build.setScope("PRIVATE"); build.setOwnerUserId(7L); build.setKnowledgeBaseId(10L);
        build.setPositionId(20L); build.setStatus("RUNNING");
        when(buildMapper.selectById(99L)).thenReturn(build);
        when(candidateMapper.selectList(any(QueryWrapper.class))).thenReturn(List.of(candidate()));
        QuestionBankBuildService service = newService(kbMapper, positionMapper, buildMapper, candidateMapper, assembler, mock(KnowledgeWorkspaceService.class));

        assertThat(service.candidates(7L, 10L, 99L)).hasSize(1);
        QuestionBankBuildCandidateReviewRequest review = new QuestionBankBuildCandidateReviewRequest(); review.setAction("ACCEPT");
        assertThatThrownBy(() -> service.review(7L, 10L, 99L, 1L, review))
                .hasMessageContaining("尚未完成");
        verify(candidateMapper, never()).updateById(any());
    }

    private QuestionBankBuildCandidate candidate() { QuestionBankBuildCandidate c = new QuestionBankBuildCandidate(); c.setId(1L); c.setBuildId(99L); c.setOwnerUserId(7L); c.setStableAtomId("stable-1"); c.setSubject("JVM"); c.setCategory("java"); c.setDifficulty("mid"); c.setPrinciples("回答"); c.setFollowUpPathsJson("[\"深入\",\"引导\"]"); c.setSourceRef("notes.md#chunk-0"); c.setSourceEvidenceJson("[{\"quote\":\"evidence\"}]"); c.setReviewStatus("PENDING"); return c; }
    private KnowledgeBase privateKb() { KnowledgeBase kb = new KnowledgeBase(); kb.setId(10L); kb.setScope("PRIVATE"); kb.setOwnerUserId(7L); kb.setPositionId(20L); kb.setStatus("ACTIVE"); return kb; }
    private InterviewPosition privatePosition() { InterviewPosition p = new InterviewPosition(); p.setId(20L); p.setScope("PRIVATE"); p.setOwnerUserId(7L); p.setStatus("ACTIVE"); return p; }
    private QuestionBankBuildService newService(KnowledgeBaseMapper kb, InterviewPositionMapper position, QuestionBankBuildMapper build, QuestionBankBuildCandidateMapper candidate, QuestionBankBuildResponseAssembler assembler, KnowledgeWorkspaceService workspace) { QuestionBankAccessProperties access = new QuestionBankAccessProperties(); access.setUserMaintenanceEnabled(true); return new QuestionBankBuildService(kb, position, mock(KnowledgeSourceFileMapper.class), build, candidate, mock(AppJobMapper.class), mock(UserLlmConfigService.class), new QuestionBankBuildProperties(), access, mock(QuestionBankBuildInputService.class), mock(QuestionBankBuildFileStorage.class), workspace, assembler, mock(QuestionBankBuildCreationService.class)); }
}
