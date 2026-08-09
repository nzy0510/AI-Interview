package com.interview.service.questionbank.build;

import com.interview.entity.QuestionBankBuild;
import com.interview.entity.QuestionBankBuildCandidate;
import com.interview.entity.KnowledgeBase;
import com.interview.dto.questionbank.QuestionBankSearchRequest;
import com.interview.dto.questionbank.QuestionBankSearchResponse;
import com.interview.dto.questionbank.QuestionBankSearchResult;
import com.interview.service.UserLlmRuntimeConfig;
import com.interview.mapper.KnowledgeBaseMapper;
import com.interview.service.questionbank.QuestionBankService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class QuestionBankBuildSupervisionServiceTest {

    @Test
    void shouldRequireHumanReviewBeforeCallingModelWhenEvidenceIsNotInSource() {
        QuestionBankBuildLlm llm = mock(QuestionBankBuildLlm.class);
        QuestionBankService questionBankService = mock(QuestionBankService.class);
        QuestionBankBuildSupervisionService service = new QuestionBankBuildSupervisionService(
                llm, questionBankService, mock(KnowledgeBaseMapper.class));

        QuestionBankBuild build = new QuestionBankBuild();
        build.setId(11L);
        build.setScope("PRIVATE");
        build.setOwnerUserId(7L);
        build.setPositionId(20L);
        build.setKnowledgeBaseId(30L);
        QuestionBankBuildCandidate candidate = candidate();

        QuestionBankBuildSupervisionResult result = service.review(new QuestionBankBuildSupervisionContext(
                build,
                candidate,
                "JVM 类加载遵循双亲委派模型。",
                runtime(),
                List.of(candidate)
        ));

        assertThat(result.status()).isEqualTo("NEEDS_HUMAN");
        assertThat(result.issues()).contains("来源证据无法在原文片段中定位");
        verifyNoInteractions(llm, questionBankService);
    }

    @Test
    void shouldAutoPassOnlyAfterReviewingSourceAndScopedSimilarAtoms() {
        QuestionBankBuildLlm llm = mock(QuestionBankBuildLlm.class);
        QuestionBankService questionBankService = mock(QuestionBankService.class);
        KnowledgeBaseMapper knowledgeBaseMapper = mock(KnowledgeBaseMapper.class);
        when(knowledgeBaseMapper.selectById(30L)).thenReturn(knowledgeBase("PRIVATE", 7L));
        QuestionBankBuildSupervisionService service = new QuestionBankBuildSupervisionService(
                llm, questionBankService, knowledgeBaseMapper);
        QuestionBankBuild build = new QuestionBankBuild();
        build.setId(11L);
        build.setScope("PRIVATE");
        build.setOwnerUserId(7L);
        build.setPositionId(20L);
        build.setKnowledgeBaseId(30L);
        QuestionBankBuildCandidate candidate = candidate();
        candidate.setSourceEvidenceJson("[{\"quote\":\"双亲委派模型\",\"pageOrSection\":\"chunk-0\"}]");
        QuestionBankSearchResult similar = QuestionBankSearchResult.builder()
                .atomId("kb30-existing")
                .subject("类加载器层级")
                .promptContext("考核点: 类加载器层级")
                .score(0.82)
                .build();
        when(questionBankService.searchWithMetadata(any(QuestionBankSearchRequest.class)))
                .thenReturn(QuestionBankSearchResponse.builder().results(List.of(similar)).strategy("QDRANT_VECTOR").build());
        when(llm.complete(any(), contains("题库质量监督器"), any()))
                .thenReturn("{\"verdict\":\"PASS\",\"confidence\":0.92,\"issues\":[],\"duplicateHint\":\"\"}");

        QuestionBankBuildSupervisionResult result = service.review(new QuestionBankBuildSupervisionContext(
                build,
                candidate,
                "JVM 类加载遵循双亲委派模型。",
                runtime(),
                List.of(candidate)
        ));

        assertThat(result.status()).isEqualTo("AUTO_PASS");
        assertThat(result.score()).isEqualTo(0.92);
        verify(llm).complete(any(), contains("题库质量监督器"), contains("JVM 类加载遵循双亲委派模型"));
        ArgumentCaptor<QuestionBankSearchRequest> request = ArgumentCaptor.forClass(QuestionBankSearchRequest.class);
        verify(questionBankService).searchWithMetadata(request.capture());
        assertThat(request.getValue().getOwnerUserId()).isEqualTo(7L);
        assertThat(request.getValue().getPositionId()).isEqualTo(20L);
        assertThat(request.getValue().getKnowledgeBaseId()).isEqualTo(30L);
    }

    @Test
    void shouldSearchPublicTargetAtomsWithoutLeakingTheInitiatingAdminAsOwner() {
        QuestionBankBuildLlm llm = mock(QuestionBankBuildLlm.class);
        QuestionBankService questionBankService = mock(QuestionBankService.class);
        KnowledgeBaseMapper knowledgeBaseMapper = mock(KnowledgeBaseMapper.class);
        when(knowledgeBaseMapper.selectById(30L)).thenReturn(knowledgeBase("PUBLIC", null));
        when(questionBankService.searchWithMetadata(any(QuestionBankSearchRequest.class)))
                .thenReturn(QuestionBankSearchResponse.builder().results(List.of()).strategy("EMPTY").build());
        when(llm.complete(any(), any(), any()))
                .thenReturn("{\"verdict\":\"PASS\",\"confidence\":0.9,\"issues\":[]}");
        QuestionBankBuildSupervisionService service = new QuestionBankBuildSupervisionService(
                llm, questionBankService, knowledgeBaseMapper);
        QuestionBankBuild build = new QuestionBankBuild();
        build.setId(11L); build.setScope("PRIVATE"); build.setOwnerUserId(7L);
        build.setPositionId(20L); build.setKnowledgeBaseId(30L);
        QuestionBankBuildCandidate candidate = candidate();
        candidate.setSourceEvidenceJson("[{\"quote\":\"双亲委派模型\"}]");

        service.review(new QuestionBankBuildSupervisionContext(
                build, candidate, "JVM 类加载遵循双亲委派模型。", runtime(), List.of(candidate)));

        ArgumentCaptor<QuestionBankSearchRequest> request = ArgumentCaptor.forClass(QuestionBankSearchRequest.class);
        verify(questionBankService).searchWithMetadata(request.capture());
        assertThat(request.getValue().getScope()).isEqualTo("PUBLIC");
        assertThat(request.getValue().getOwnerUserId()).isNull();
    }

    private QuestionBankBuildCandidate candidate() {
        QuestionBankBuildCandidate candidate = new QuestionBankBuildCandidate();
        candidate.setId(101L);
        candidate.setBuildId(11L);
        candidate.setSubject("JVM 类加载");
        candidate.setCategory("jvm");
        candidate.setDifficulty("mid");
        candidate.setPrinciples("双亲委派");
        candidate.setFollowUpPathsJson("[\"深入追问\",\"引导追问\"]");
        candidate.setSourceEvidenceJson("[{\"quote\":\"这句并不存在\",\"pageOrSection\":\"chunk-0\"}]");
        return candidate;
    }

    private UserLlmRuntimeConfig runtime() {
        return new UserLlmRuntimeConfig(3L, 7L, "test", "test", "http://localhost", "mock", "secret", 0.0);
    }

    private KnowledgeBase knowledgeBase(String scope, Long ownerUserId) {
        KnowledgeBase knowledgeBase = new KnowledgeBase();
        knowledgeBase.setId(30L);
        knowledgeBase.setPositionId(20L);
        knowledgeBase.setScope(scope);
        knowledgeBase.setOwnerUserId(ownerUserId);
        knowledgeBase.setStatus("ACTIVE");
        return knowledgeBase;
    }
}
