package com.interview.service.questionbank;

import com.interview.entity.KnowledgeSourceFile;
import com.interview.exception.LlmProviderRequiredException;
import com.interview.mapper.AppJobMapper;
import com.interview.mapper.KnowledgeSourceFileMapper;
import com.interview.service.AdminRoleService;
import com.interview.service.AppJobRecoveryService;
import com.interview.service.UserLlmConfigService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("KnowledgeAtomJobService — 原子生成作业创建")
class KnowledgeAtomJobServiceTest {

    private KnowledgeSourceFileMapper sourceFileMapper;
    private AppJobMapper appJobMapper;
    private AppJobRecoveryService appJobRecoveryService;
    private UserLlmConfigService userLlmConfigService;
    private AdminRoleService adminRoleService;
    private KnowledgeAtomJobService service;

    @BeforeEach
    void setUp() {
        sourceFileMapper = mock(KnowledgeSourceFileMapper.class);
        appJobMapper = mock(AppJobMapper.class);
        appJobRecoveryService = mock(AppJobRecoveryService.class);
        userLlmConfigService = mock(UserLlmConfigService.class);
        adminRoleService = mock(AdminRoleService.class);
        service = new KnowledgeAtomJobService(
                sourceFileMapper,
                appJobMapper,
                appJobRecoveryService,
                userLlmConfigService,
                adminRoleService
        );
    }

    @Test
    @DisplayName("普通用户不能为公共源文件创建原子生成作业")
    void shouldRejectPublicSourceGenerationForNormalUser() {
        KnowledgeSourceFile sourceFile = convertedSourceFile("PUBLIC", null);
        when(sourceFileMapper.selectById(10L)).thenReturn(sourceFile);
        when(adminRoleService.isAdmin(7L)).thenReturn(false);

        assertThatThrownBy(() -> service.createGenerationJob(10L, 7L))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("无权访问文件");

        verify(appJobMapper, never()).insert(any());
    }

    @Test
    @DisplayName("创建生成作业前要求当前用户已配置启用的大模型")
    void shouldRequireActiveLlmProviderBeforeCreatingGenerationJob() {
        KnowledgeSourceFile sourceFile = convertedSourceFile("PRIVATE", 7L);
        when(sourceFileMapper.selectById(10L)).thenReturn(sourceFile);
        when(userLlmConfigService.requireActiveRuntimeConfig(7L)).thenThrow(new LlmProviderRequiredException());

        assertThatThrownBy(() -> service.createGenerationJob(10L, 7L))
                .isInstanceOf(LlmProviderRequiredException.class)
                .hasMessageContaining("请先配置大模型 API");

        verify(appJobMapper, never()).insert(any());
    }

    private KnowledgeSourceFile convertedSourceFile(String scope, Long ownerUserId) {
        KnowledgeSourceFile sourceFile = new KnowledgeSourceFile();
        sourceFile.setId(10L);
        sourceFile.setScope(scope);
        sourceFile.setOwnerUserId(ownerUserId);
        sourceFile.setPositionId(12L);
        sourceFile.setKnowledgeBaseId(22L);
        sourceFile.setOriginalFilename("java.md");
        sourceFile.setMarkdownStorageKey("markdown/10.md");
        sourceFile.setStatus("CONVERTED");
        sourceFile.setCreatedBy(ownerUserId);
        return sourceFile;
    }
}
