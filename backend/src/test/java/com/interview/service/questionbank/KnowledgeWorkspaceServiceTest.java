package com.interview.service.questionbank;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.interview.entity.InterviewPosition;
import com.interview.entity.KnowledgeAtom;
import com.interview.entity.KnowledgeBase;
import com.interview.dto.questionbank.QuestionBankImportRequest;
import com.interview.dto.questionbank.QuestionBankImportResult;
import com.interview.mapper.AppJobMapper;
import com.interview.mapper.InterviewPositionMapper;
import com.interview.mapper.KnowledgeAtomMapper;
import com.interview.mapper.KnowledgeAtomReviewMapper;
import com.interview.mapper.KnowledgeAtomVersionMapper;
import com.interview.mapper.KnowledgeBaseMapper;
import com.interview.mapper.KnowledgeSourceFileMapper;
import com.interview.service.AdminRoleService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("KnowledgeWorkspaceService — 用户知识库工作台")
class KnowledgeWorkspaceServiceTest {

    private InterviewPositionMapper positionMapper;
    private KnowledgeBaseMapper knowledgeBaseMapper;
    private KnowledgeAtomMapper atomMapper;
    private KnowledgeAtomVersionMapper versionMapper;
    private KnowledgeAtomReviewMapper reviewMapper;
    private KnowledgeSourceFileMapper sourceFileMapper;
    private AppJobMapper appJobMapper;
    private AdminRoleService adminRoleService;
    private QuestionBankService questionBankService;
    private QdrantVectorService qdrantVectorService;
    private KnowledgeWorkspaceService service;

    @BeforeEach
    void setUp() {
        positionMapper = mock(InterviewPositionMapper.class);
        knowledgeBaseMapper = mock(KnowledgeBaseMapper.class);
        atomMapper = mock(KnowledgeAtomMapper.class);
        versionMapper = mock(KnowledgeAtomVersionMapper.class);
        reviewMapper = mock(KnowledgeAtomReviewMapper.class);
        sourceFileMapper = mock(KnowledgeSourceFileMapper.class);
        appJobMapper = mock(AppJobMapper.class);
        adminRoleService = mock(AdminRoleService.class);
        questionBankService = mock(QuestionBankService.class);
        qdrantVectorService = mock(QdrantVectorService.class);
        service = new KnowledgeWorkspaceService(positionMapper, knowledgeBaseMapper,
                atomMapper, versionMapper, reviewMapper, sourceFileMapper,
                appJobMapper, adminRoleService, questionBankService, qdrantVectorService);
    }

    @Test
    @DisplayName("岗位列表包含公开岗位和当前用户私有岗位，并带默认知识库和维护能力")
    void shouldListPublicAndOwnedPrivatePositionsWithKnowledgeBaseCapabilities() {
        InterviewPosition publicPosition = position(1L, "PUBLIC", null, "Java 后端开发", "ACTIVE", 11L);
        InterviewPosition privatePosition = position(2L, "PRIVATE", 7L, "我的后端岗", "ACTIVE", 12L);
        when(positionMapper.selectList(any(QueryWrapper.class))).thenReturn(List.of(publicPosition, privatePosition));
        when(knowledgeBaseMapper.selectList(any(QueryWrapper.class))).thenReturn(List.of(
                knowledgeBase(11L, 1L, "PUBLIC", null),
                knowledgeBase(12L, 2L, "PRIVATE", 7L)
        ));

        KnowledgeWorkspaceResponse response = service.listWorkspace(7L);

        assertThat(response.positions()).hasSize(2);
        assertThat(response.positions().get(0).scope()).isEqualTo("PUBLIC");
        assertThat(response.positions().get(0).editable()).isFalse();
        assertThat(response.positions().get(0).canImportPackage()).isFalse();
        assertThat(response.positions().get(0).canManageAtoms()).isFalse();
        assertThat(response.positions().get(1).scope()).isEqualTo("PRIVATE");
        assertThat(response.positions().get(1).editable()).isTrue();
        assertThat(response.positions().get(1).canImportPackage()).isTrue();
        assertThat(response.positions().get(1).canManageAtoms()).isTrue();
        assertThat(response.positions().get(1).canArchiveAtoms()).isTrue();
        assertThat(response.positions().get(1).canPublishAtoms()).isTrue();
        assertThat(response.positions().get(1).canReindexAtoms()).isTrue();
        assertThat(response.positions().get(1).knowledgeBase().id()).isEqualTo(12L);
    }

    @Test
    @DisplayName("管理员可通过同一工作台维护公共题库")
    void shouldExposePublicQuestionBankMaintenanceCapabilitiesForAdmin() {
        InterviewPosition publicPosition = position(1L, "PUBLIC", null, "Java 后端开发", "ACTIVE", 11L);
        when(positionMapper.selectList(any(QueryWrapper.class))).thenReturn(List.of(publicPosition));
        when(knowledgeBaseMapper.selectList(any(QueryWrapper.class))).thenReturn(List.of(
                knowledgeBase(11L, 1L, "PUBLIC", null)
        ));
        when(adminRoleService.isAdmin(7L)).thenReturn(true);

        KnowledgePositionResponse position = service.listWorkspace(7L).positions().get(0);

        assertThat(position.scope()).isEqualTo("PUBLIC");
        assertThat(position.editable()).isFalse();
        assertThat(position.canImportPackage()).isTrue();
        assertThat(position.canManageAtoms()).isTrue();
        assertThat(position.canArchiveAtoms()).isTrue();
        assertThat(position.canPublishAtoms()).isTrue();
        assertThat(position.canReindexAtoms()).isTrue();
    }

    @Test
    @DisplayName("创建私有岗位时自动创建默认知识库")
    void shouldCreatePrivatePositionWithDefaultKnowledgeBase() {
        doAnswer(invocation -> {
            InterviewPosition position = invocation.getArgument(0);
            position.setId(20L);
            return 1;
        }).when(positionMapper).insert(any(InterviewPosition.class));
        doAnswer(invocation -> {
            KnowledgeBase knowledgeBase = invocation.getArgument(0);
            knowledgeBase.setId(30L);
            return 1;
        }).when(knowledgeBaseMapper).insert(any(KnowledgeBase.class));
        KnowledgePositionCreateRequest request = new KnowledgePositionCreateRequest("算法工程师", "机器学习面试");

        KnowledgePositionResponse response = service.createPrivatePosition(7L, request);

        assertThat(response.id()).isEqualTo(20L);
        assertThat(response.scope()).isEqualTo("PRIVATE");
        assertThat(response.editable()).isTrue();
        assertThat(response.knowledgeBase().id()).isEqualTo(30L);
        verify(positionMapper).updateById(any(InterviewPosition.class));
    }

    @Test
    @DisplayName("岗位说明长度与前端输入限制保持一致")
    void shouldRejectDescriptionLongerThanFrontendLimit() {
        String tooLong = "a".repeat(301);

        assertThatThrownBy(() -> service.createPrivatePosition(7L,
                new KnowledgePositionCreateRequest("算法工程师", tooLong)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("岗位说明不能超过 300 个字符");

        verify(positionMapper, never()).insert(any());
    }

    @Test
    @DisplayName("普通用户只能删除自己的私有岗位")
    void shouldDeleteOwnedPrivatePositionOnly() {
        InterviewPosition position = position(20L, "PRIVATE", 7L, "我的岗位", "ACTIVE", 30L);
        when(positionMapper.selectById(20L)).thenReturn(position);
        when(atomMapper.selectList(any(QueryWrapper.class))).thenReturn(List.of());

        service.deletePrivatePosition(7L, 20L);

        verify(positionMapper).deleteById(20L);
    }

    @Test
    @DisplayName("不能删除公开岗位或他人的私有岗位")
    void shouldRejectDeletingPublicOrOtherUsersPosition() {
        when(positionMapper.selectById(1L)).thenReturn(position(1L, "PUBLIC", null, "Java 后端开发", "ACTIVE", 11L));

        assertThatThrownBy(() -> service.deletePrivatePosition(7L, 1L))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("无权访问");

        verify(positionMapper, never()).deleteById(any(Long.class));
    }

    @Test
    @DisplayName("当前用户可将导入包导入自己的私有知识库草稿")
    void shouldImportPackageIntoOwnedPrivateKnowledgeBaseAsDraft() {
        KnowledgeBase knowledgeBase = knowledgeBase(30L, 20L, "PRIVATE", 7L);
        when(knowledgeBaseMapper.selectById(30L)).thenReturn(knowledgeBase);
        QuestionBankImportRequest request = new QuestionBankImportRequest();
        request.setMode("AUTO_PUBLISH");
        when(questionBankService.importBatch(any(), any())).thenReturn(QuestionBankImportResult.builder()
                .batchId("qb-private")
                .mode("DRAFT")
                .received(1)
                .imported(1)
                .build());

        QuestionBankImportResult result = service.importPackage(7L, 30L, request);

        assertThat(result.getMode()).isEqualTo("DRAFT");
        ArgumentCaptor<QuestionBankImportScope> scopeCaptor = ArgumentCaptor.forClass(QuestionBankImportScope.class);
        verify(questionBankService).importBatch(any(QuestionBankImportRequest.class), scopeCaptor.capture());
        assertThat(scopeCaptor.getValue().scope()).isEqualTo("PRIVATE");
        assertThat(scopeCaptor.getValue().ownerUserId()).isEqualTo(7L);
        assertThat(scopeCaptor.getValue().positionId()).isEqualTo(20L);
        assertThat(scopeCaptor.getValue().knowledgeBaseId()).isEqualTo(30L);
        assertThat(scopeCaptor.getValue().allowAutoPublish()).isFalse();
    }

    @Test
    @DisplayName("普通用户不能向公共知识库导入题库包")
    void shouldRejectPackageImportIntoPublicKnowledgeBaseForNormalUser() {
        KnowledgeBase knowledgeBase = knowledgeBase(11L, 1L, "PUBLIC", null);
        when(knowledgeBaseMapper.selectById(11L)).thenReturn(knowledgeBase);
        when(adminRoleService.isAdmin(7L)).thenReturn(false);

        assertThatThrownBy(() -> service.importPackage(7L, 11L, new QuestionBankImportRequest()))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("无权访问知识库");

        verify(questionBankService, never()).importBatch(any(), any());
    }

    private InterviewPosition position(Long id, String scope, Long ownerUserId, String name, String status, Long defaultKnowledgeBaseId) {
        InterviewPosition position = new InterviewPosition();
        position.setId(id);
        position.setScope(scope);
        position.setOwnerUserId(ownerUserId);
        position.setName(name);
        position.setStatus(status);
        position.setDefaultKnowledgeBaseId(defaultKnowledgeBaseId);
        return position;
    }

    private KnowledgeBase knowledgeBase(Long id, Long positionId, String scope, Long ownerUserId) {
        KnowledgeBase knowledgeBase = new KnowledgeBase();
        knowledgeBase.setId(id);
        knowledgeBase.setPositionId(positionId);
        knowledgeBase.setScope(scope);
        knowledgeBase.setOwnerUserId(ownerUserId);
        knowledgeBase.setName("默认知识库");
        knowledgeBase.setStatus("ACTIVE");
        return knowledgeBase;
    }

}
