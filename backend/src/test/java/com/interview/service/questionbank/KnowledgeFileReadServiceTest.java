package com.interview.service.questionbank;

import com.interview.entity.KnowledgeSourceFile;
import com.interview.mapper.KnowledgeSourceFileMapper;
import com.interview.service.AdminRoleService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("KnowledgeFileReadService — 题库文件读取权限")
class KnowledgeFileReadServiceTest {

    @Test
    @DisplayName("普通用户可以读取公开文件的 Markdown")
    void shouldAllowPublicMarkdownRead() throws Exception {
        KnowledgeSourceFileMapper sourceFileMapper = mock(KnowledgeSourceFileMapper.class);
        AdminRoleService adminRoleService = mock(AdminRoleService.class);
        FileStorageService fileStorageService = mock(FileStorageService.class);
        KnowledgeSourceFile sourceFile = sourceFile(101L, "PUBLIC", null);
        when(sourceFileMapper.selectById(101L)).thenReturn(sourceFile);
        when(fileStorageService.readText("knowledge/markdown/a.md")).thenReturn("# Public");
        KnowledgeFileReadService service = new KnowledgeFileReadService(
                sourceFileMapper, adminRoleService, fileStorageService);

        String markdown = service.readMarkdown(101L, 7L);

        assertThat(markdown).isEqualTo("# Public");
    }

    @Test
    @DisplayName("普通用户不能读取他人的私有文件")
    void shouldRejectCrossUserPrivateRead() {
        KnowledgeSourceFileMapper sourceFileMapper = mock(KnowledgeSourceFileMapper.class);
        AdminRoleService adminRoleService = mock(AdminRoleService.class);
        FileStorageService fileStorageService = mock(FileStorageService.class);
        when(sourceFileMapper.selectById(101L)).thenReturn(sourceFile(101L, "PRIVATE", 8L));
        when(adminRoleService.isAdmin(7L)).thenReturn(false);
        KnowledgeFileReadService service = new KnowledgeFileReadService(
                sourceFileMapper, adminRoleService, fileStorageService);

        assertThatThrownBy(() -> service.readMarkdown(101L, 7L))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("无权访问");
    }

    private KnowledgeSourceFile sourceFile(Long id, String scope, Long ownerUserId) {
        KnowledgeSourceFile sourceFile = new KnowledgeSourceFile();
        sourceFile.setId(id);
        sourceFile.setScope(scope);
        sourceFile.setOwnerUserId(ownerUserId);
        sourceFile.setOriginalFilename("a.md");
        sourceFile.setStorageKey("knowledge/originals/a.md");
        sourceFile.setMarkdownStorageKey("knowledge/markdown/a.md");
        sourceFile.setStatus("CONVERTED");
        return sourceFile;
    }
}
