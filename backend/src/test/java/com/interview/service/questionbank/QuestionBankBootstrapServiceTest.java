package com.interview.service.questionbank;

import com.interview.dto.questionbank.QuestionBankImportRequest;
import com.interview.entity.InterviewPosition;
import com.interview.entity.KnowledgeAtomImportBatch;
import com.interview.mapper.KnowledgeAtomImportBatchMapper;
import com.interview.mapper.InterviewPositionMapper;
import com.interview.mapper.KnowledgeBaseMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("QuestionBankBootstrapService — scoped public seed")
@ExtendWith(MockitoExtension.class)
class QuestionBankBootstrapServiceTest {

    @Mock
    private QuestionBankService questionBankService;

    @Mock
    private InterviewPositionMapper positionMapper;

    @Mock
    private KnowledgeBaseMapper knowledgeBaseMapper;

    @Mock
    private KnowledgeAtomImportBatchMapper batchMapper;

    @Test
    @DisplayName("内置公共导入包会使用公共岗位作用域并自动发布")
    void shouldSeedBuiltInPackagesWithPublicScope() {
        when(batchMapper.selectOne(any())).thenReturn(null);
        when(positionMapper.selectOne(any())).thenReturn(publicPosition());
        QuestionBankBootstrapService service = new QuestionBankBootstrapService(
                questionBankService, positionMapper, knowledgeBaseMapper, batchMapper);
        ReflectionTestUtils.setField(service, "seedFromJson", true);
        ReflectionTestUtils.setField(service, "reindexUnsyncedOnStartup", false);

        service.init();

        ArgumentCaptor<QuestionBankImportRequest> requestCaptor = ArgumentCaptor.forClass(QuestionBankImportRequest.class);
        ArgumentCaptor<QuestionBankImportScope> scopeCaptor = ArgumentCaptor.forClass(QuestionBankImportScope.class);
        verify(questionBankService, atLeastOnce()).importBatch(requestCaptor.capture(), scopeCaptor.capture());
        assertThat(requestCaptor.getAllValues())
                .allSatisfy(request -> assertThat(request.getTargetCategory()).isIn(
                        "Java 后端开发",
                        "Web 前端开发",
                        "AI 大模型应用开发"));
        List<QuestionBankImportScope> scopes = scopeCaptor.getAllValues();
        assertThat(scopes).isNotEmpty();
        assertThat(scopes)
                .allSatisfy(scope -> {
                    assertThat(scope.scope()).isEqualTo("PUBLIC");
                    assertThat(scope.positionId()).isEqualTo(101L);
                    assertThat(scope.knowledgeBaseId()).isEqualTo(201L);
                    assertThat(scope.allowAutoPublish()).isTrue();
                });
    }

    @Test
    @DisplayName("已导入的内置公共导入包不会重复导入")
    void shouldSkipAlreadyImportedBuiltInPackages() {
        when(batchMapper.selectOne(any())).thenReturn(importedBatch());
        QuestionBankBootstrapService service = new QuestionBankBootstrapService(
                questionBankService, positionMapper, knowledgeBaseMapper, batchMapper);
        ReflectionTestUtils.setField(service, "seedFromJson", true);
        ReflectionTestUtils.setField(service, "reindexUnsyncedOnStartup", false);

        service.init();

        verify(questionBankService, never()).importBatch(any(), any());
    }

    @Test
    @DisplayName("未完成的内置公共导入批次会清理后重试")
    void shouldRetryIncompleteBuiltInPackageBatch() {
        when(batchMapper.selectOne(any()))
                .thenReturn(failedBatch())
                .thenReturn(null);
        when(positionMapper.selectOne(any())).thenReturn(publicPosition());
        QuestionBankBootstrapService service = new QuestionBankBootstrapService(
                questionBankService, positionMapper, knowledgeBaseMapper, batchMapper);
        ReflectionTestUtils.setField(service, "seedFromJson", true);
        ReflectionTestUtils.setField(service, "reindexUnsyncedOnStartup", false);

        service.init();

        verify(batchMapper, atLeastOnce()).delete(any());
        verify(questionBankService, atLeastOnce()).importBatch(any(), any());
    }

    @Test
    @DisplayName("启动时会归档被内置包替换或移除的旧公共原子")
    void shouldRetireLegacyBuiltInAtoms() {
        when(batchMapper.selectOne(any())).thenReturn(importedBatch());
        when(questionBankService.archiveAtoms(any())).thenReturn(java.util.Map.of("archived", 3));
        QuestionBankBootstrapService service = new QuestionBankBootstrapService(
                questionBankService, positionMapper, knowledgeBaseMapper, batchMapper);
        ReflectionTestUtils.setField(service, "seedFromJson", true);
        ReflectionTestUtils.setField(service, "reindexUnsyncedOnStartup", false);

        service.init();

        ArgumentCaptor<List<String>> idsCaptor = ArgumentCaptor.forClass((Class) List.class);
        verify(questionBankService).archiveAtoms(idsCaptor.capture());
        assertThat(idsCaptor.getValue())
                .contains("common-001", "common-002", "common-003", "agent-dead-loop-resolution");
    }

    private InterviewPosition publicPosition() {
        return publicPosition(101L, "Java 后端开发", 201L);
    }

    private InterviewPosition publicPosition(Long id, String name, Long defaultKnowledgeBaseId) {
        InterviewPosition position = new InterviewPosition();
        position.setId(id);
        position.setName(name);
        position.setScope("PUBLIC");
        position.setStatus("ACTIVE");
        position.setDefaultKnowledgeBaseId(defaultKnowledgeBaseId);
        return position;
    }

    private KnowledgeAtomImportBatch importedBatch() {
        KnowledgeAtomImportBatch batch = new KnowledgeAtomImportBatch();
        batch.setBatchId("seed-public-java-backend-20260616");
        batch.setStatus("IMPORTED");
        return batch;
    }

    private KnowledgeAtomImportBatch failedBatch() {
        KnowledgeAtomImportBatch batch = new KnowledgeAtomImportBatch();
        batch.setBatchId("seed-public-java-backend-20260616");
        batch.setStatus("FAILED");
        return batch;
    }
}
