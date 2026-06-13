package com.interview.service.questionbank;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.interview.entity.InterviewPosition;
import com.interview.entity.KnowledgeBase;
import com.interview.entity.KnowledgeSourceFile;
import com.interview.mapper.InterviewPositionMapper;
import com.interview.mapper.KnowledgeBaseMapper;
import com.interview.mapper.KnowledgeSourceFileMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("KnowledgeWorkspaceService — 用户知识库工作台")
class KnowledgeWorkspaceServiceTest {

    private InterviewPositionMapper positionMapper;
    private KnowledgeBaseMapper knowledgeBaseMapper;
    private KnowledgeSourceFileMapper sourceFileMapper;
    private KnowledgeWorkspaceService service;

    @BeforeEach
    void setUp() {
        positionMapper = mock(InterviewPositionMapper.class);
        knowledgeBaseMapper = mock(KnowledgeBaseMapper.class);
        sourceFileMapper = mock(KnowledgeSourceFileMapper.class);
        service = new KnowledgeWorkspaceService(positionMapper, knowledgeBaseMapper, sourceFileMapper);
    }

    @Test
    @DisplayName("岗位列表包含公开岗位和当前用户私有岗位，并带默认知识库文件")
    void shouldListPublicAndOwnedPrivatePositionsWithFiles() {
        InterviewPosition publicPosition = position(1L, "PUBLIC", null, "Java 后端开发", "ACTIVE", 11L);
        InterviewPosition privatePosition = position(2L, "PRIVATE", 7L, "我的后端岗", "ACTIVE", 12L);
        when(positionMapper.selectList(any(QueryWrapper.class))).thenReturn(List.of(publicPosition, privatePosition));
        when(knowledgeBaseMapper.selectList(any(QueryWrapper.class))).thenReturn(List.of(
                knowledgeBase(11L, 1L, "PUBLIC", null),
                knowledgeBase(12L, 2L, "PRIVATE", 7L)
        ));
        when(sourceFileMapper.selectList(any(QueryWrapper.class))).thenReturn(List.of(
                sourceFile(101L, 12L, "PRIVATE", 7L, "guide.md"),
                sourceFile(102L, 12L, "PRIVATE", 8L, "other-user.md")
        ));

        KnowledgeWorkspaceResponse response = service.listWorkspace(7L);

        assertThat(response.positions()).hasSize(2);
        assertThat(response.positions().get(0).scope()).isEqualTo("PUBLIC");
        assertThat(response.positions().get(0).editable()).isFalse();
        assertThat(response.positions().get(1).scope()).isEqualTo("PRIVATE");
        assertThat(response.positions().get(1).editable()).isTrue();
        assertThat(response.positions().get(1).knowledgeBase().sourceFiles()).hasSize(1);
        assertThat(response.positions().get(1).knowledgeBase().sourceFiles().get(0).originalFilename()).isEqualTo("guide.md");
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
    @DisplayName("普通用户只能归档自己的私有岗位")
    void shouldArchiveOwnedPrivatePositionOnly() {
        InterviewPosition position = position(20L, "PRIVATE", 7L, "我的岗位", "ACTIVE", 30L);
        when(positionMapper.selectById(20L)).thenReturn(position);

        service.archivePrivatePosition(7L, 20L);

        assertThat(position.getStatus()).isEqualTo("ARCHIVED");
        verify(positionMapper).updateById(position);
        verify(knowledgeBaseMapper).update(isNull(), any(UpdateWrapper.class));
    }

    @Test
    @DisplayName("不能归档公开岗位或他人的私有岗位")
    void shouldRejectArchivingPublicOrOtherUsersPosition() {
        when(positionMapper.selectById(1L)).thenReturn(position(1L, "PUBLIC", null, "Java 后端开发", "ACTIVE", 11L));

        assertThatThrownBy(() -> service.archivePrivatePosition(7L, 1L))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("无权访问");

        verify(positionMapper, never()).updateById(any());
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

    private KnowledgeSourceFile sourceFile(Long id, Long knowledgeBaseId, String scope, Long ownerUserId, String filename) {
        KnowledgeSourceFile sourceFile = new KnowledgeSourceFile();
        sourceFile.setId(id);
        sourceFile.setKnowledgeBaseId(knowledgeBaseId);
        sourceFile.setScope(scope);
        sourceFile.setOwnerUserId(ownerUserId);
        sourceFile.setOriginalFilename(filename);
        sourceFile.setStatus("CONVERTED");
        return sourceFile;
    }
}
