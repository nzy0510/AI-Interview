package com.interview.service.questionbank.build;

import com.interview.config.QuestionBankAccessProperties;
import com.interview.entity.InterviewPosition;
import com.interview.entity.KnowledgeBase;
import com.interview.mapper.InterviewPositionMapper;
import com.interview.mapper.KnowledgeBaseMapper;
import com.interview.service.AdminRoleService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class QuestionBankBuildAccessServiceTest {
    private KnowledgeBaseMapper knowledgeBaseMapper;
    private InterviewPositionMapper positionMapper;
    private AdminRoleService adminRoleService;
    private QuestionBankAccessProperties accessProperties;
    private QuestionBankBuildAccessService service;

    @BeforeEach
    void setUp() {
        knowledgeBaseMapper = mock(KnowledgeBaseMapper.class);
        positionMapper = mock(InterviewPositionMapper.class);
        adminRoleService = mock(AdminRoleService.class);
        accessProperties = new QuestionBankAccessProperties();
        accessProperties.setUserMaintenanceEnabled(true);
        service = new QuestionBankBuildAccessService(
                knowledgeBaseMapper, positionMapper, adminRoleService, accessProperties);
    }

    @Test
    void shouldAllowAdminToBuildIntoAnActivePublicQuestionBank() {
        accessProperties.setUserMaintenanceEnabled(false);
        when(knowledgeBaseMapper.selectById(10L)).thenReturn(knowledgeBase("PUBLIC", null));
        when(positionMapper.selectById(20L)).thenReturn(position("PUBLIC", null));
        when(adminRoleService.isAdmin(7L)).thenReturn(true);

        KnowledgeBase result = service.requireBuildTarget(7L, 10L);

        assertThat(result.getScope()).isEqualTo("PUBLIC");
    }

    @Test
    void shouldRejectOrdinaryUserBuildingIntoAPublicQuestionBank() {
        when(knowledgeBaseMapper.selectById(10L)).thenReturn(knowledgeBase("PUBLIC", null));
        when(positionMapper.selectById(20L)).thenReturn(position("PUBLIC", null));
        when(adminRoleService.isAdmin(7L)).thenReturn(false);

        assertThatThrownBy(() -> service.requireBuildTarget(7L, 10L))
                .hasMessageContaining("无权访问知识库");
    }

    @Test
    void shouldKeepPrivateQuestionBankBuildsOwnerIsolated() {
        when(knowledgeBaseMapper.selectById(10L)).thenReturn(knowledgeBase("PRIVATE", 8L));
        when(positionMapper.selectById(20L)).thenReturn(position("PRIVATE", 8L));
        when(adminRoleService.isAdmin(7L)).thenReturn(true);

        assertThatThrownBy(() -> service.requireBuildTarget(7L, 10L))
                .hasMessageContaining("无权访问知识库");
    }

    @Test
    void shouldRejectMismatchedKnowledgeBaseAndPositionScopes() {
        when(knowledgeBaseMapper.selectById(10L)).thenReturn(knowledgeBase("PUBLIC", null));
        when(positionMapper.selectById(20L)).thenReturn(position("PRIVATE", 7L));
        when(adminRoleService.isAdmin(7L)).thenReturn(true);

        assertThatThrownBy(() -> service.requireBuildTarget(7L, 10L))
                .hasMessageContaining("无权访问岗位");
    }

    private KnowledgeBase knowledgeBase(String scope, Long ownerUserId) {
        KnowledgeBase value = new KnowledgeBase();
        value.setId(10L);
        value.setScope(scope);
        value.setOwnerUserId(ownerUserId);
        value.setPositionId(20L);
        value.setStatus("ACTIVE");
        return value;
    }

    private InterviewPosition position(String scope, Long ownerUserId) {
        InterviewPosition value = new InterviewPosition();
        value.setId(20L);
        value.setScope(scope);
        value.setOwnerUserId(ownerUserId);
        value.setStatus("ACTIVE");
        return value;
    }
}
