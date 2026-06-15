package com.interview.service.questionbank;

import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.interview.entity.InterviewPosition;
import com.interview.entity.KnowledgeAtom;
import com.interview.mapper.InterviewPositionMapper;
import com.interview.mapper.KnowledgeAtomMapper;
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
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("QuestionBankBootstrapService — scoped public seed")
@ExtendWith(MockitoExtension.class)
class QuestionBankBootstrapServiceTest {

    @Mock
    private KnowledgeAtomMapper atomMapper;

    @Mock
    private QuestionBankService questionBankService;

    @Mock
    private InterviewPositionMapper positionMapper;

    @Mock
    private KnowledgeBaseMapper knowledgeBaseMapper;

    @Test
    @DisplayName("空库 legacy JSON seed 会使用公共岗位作用域导入")
    void shouldSeedLegacyAtomsWithPublicScope() {
        when(atomMapper.selectCount(any())).thenReturn(0L);
        when(positionMapper.selectOne(any())).thenReturn(publicPosition());
        QuestionBankBootstrapService service = new QuestionBankBootstrapService(
                atomMapper, questionBankService, positionMapper, knowledgeBaseMapper);
        ReflectionTestUtils.setField(service, "seedFromJson", true);
        ReflectionTestUtils.setField(service, "reindexUnsyncedOnStartup", false);

        service.init();

        ArgumentCaptor<QuestionBankImportScope> scopeCaptor = ArgumentCaptor.forClass(QuestionBankImportScope.class);
        verify(questionBankService, atLeastOnce()).importBatch(any(), scopeCaptor.capture());
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
    @DisplayName("已有旧公共原子时启动会补齐公共岗位归属并标记向量重建")
    void shouldBackfillExistingLegacyPublicAtomsIntoPublicPositions() {
        when(atomMapper.selectCount(any())).thenReturn(953L);
        when(positionMapper.selectOne(any())).thenReturn(
                publicPosition(101L, "Java 后端开发", 201L),
                publicPosition(102L, "Web 前端开发", 202L),
                publicPosition(103L, "AI 大模型应用开发", 203L)
        );
        when(atomMapper.update(isNull(), any(UpdateWrapper.class))).thenReturn(100, 200, 300);
        QuestionBankBootstrapService service = new QuestionBankBootstrapService(
                atomMapper, questionBankService, positionMapper, knowledgeBaseMapper);
        ReflectionTestUtils.setField(service, "seedFromJson", true);
        ReflectionTestUtils.setField(service, "reindexUnsyncedOnStartup", false);

        service.init();

        ArgumentCaptor<UpdateWrapper<KnowledgeAtom>> updateCaptor = ArgumentCaptor.forClass((Class) UpdateWrapper.class);
        verify(atomMapper, atLeastOnce()).update(isNull(), updateCaptor.capture());
        assertThat(updateCaptor.getAllValues())
                .anySatisfy(wrapper -> {
                    String sqlSet = wrapper.getSqlSet();
                    assertThat(sqlSet).contains("position_id");
                    assertThat(sqlSet).contains("knowledge_base_id");
                    assertThat(sqlSet).contains("publication_status");
                    assertThat(sqlSet).contains("vector_status");
                });
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
}
