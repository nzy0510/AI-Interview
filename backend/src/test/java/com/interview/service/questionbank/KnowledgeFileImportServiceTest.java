package com.interview.service.questionbank;

import com.interview.entity.AppJob;
import com.interview.entity.KnowledgeBase;
import com.interview.entity.KnowledgeSourceFile;
import com.interview.mapper.AppJobMapper;
import com.interview.mapper.KnowledgeBaseMapper;
import com.interview.mapper.KnowledgeSourceFileMapper;
import com.interview.service.AdminRoleService;
import com.interview.service.AppJobRecoveryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("KnowledgeFileImportService — 题库文件上传")
class KnowledgeFileImportServiceTest {

    @Mock
    private KnowledgeBaseMapper knowledgeBaseMapper;

    @Mock
    private KnowledgeSourceFileMapper sourceFileMapper;

    @Mock
    private AppJobMapper appJobMapper;

    @Mock
    private AdminRoleService adminRoleService;

    @Mock
    private AppJobRecoveryService appJobRecoveryService;

    @Mock
    private FileStorageService fileStorageService;

    private KnowledgeFileImportService service;

    @BeforeEach
    void setUp() {
        service = new KnowledgeFileImportService(
                knowledgeBaseMapper,
                sourceFileMapper,
                appJobMapper,
                adminRoleService,
                appJobRecoveryService,
                fileStorageService
        );
    }

    @Test
    @DisplayName("普通用户上传自己的私有知识库文件后创建转换作业")
    void shouldCreateImportJobForPrivateOwnerUpload() throws Exception {
        KnowledgeBase knowledgeBase = privateKnowledgeBase(12L, 7L, 33L);
        when(knowledgeBaseMapper.selectById(12L)).thenReturn(knowledgeBase);
        when(fileStorageService.store(any(), any()))
                .thenReturn(new FileStorageService.StoredFile(
                        "knowledge/originals/a.md", "a.md", "text/markdown", 5L, "hash"));
        doAnswer(invocation -> {
            KnowledgeSourceFile sourceFile = invocation.getArgument(0);
            sourceFile.setId(101L);
            return 1;
        }).when(sourceFileMapper).insert(any(KnowledgeSourceFile.class));
        doAnswer(invocation -> {
            AppJob job = invocation.getArgument(0);
            job.setId(202L);
            return 1;
        }).when(appJobMapper).insert(any(AppJob.class));
        MockMultipartFile file = new MockMultipartFile(
                "file", "a.md", "text/markdown", "hello".getBytes(StandardCharsets.UTF_8));

        KnowledgeFileUploadResponse response = service.upload(12L, 7L, file);

        assertThat(response.sourceFileId()).isEqualTo(101L);
        assertThat(response.jobId()).isEqualTo(202L);
        assertThat(response.status()).isEqualTo("UPLOADED");
        verify(appJobRecoveryService).dispatchJob(202L);

        ArgumentCaptor<KnowledgeSourceFile> sourceFileCaptor = ArgumentCaptor.forClass(KnowledgeSourceFile.class);
        verify(sourceFileMapper).insert(sourceFileCaptor.capture());
        assertThat(sourceFileCaptor.getValue().getScope()).isEqualTo("PRIVATE");
        assertThat(sourceFileCaptor.getValue().getOwnerUserId()).isEqualTo(7L);
        assertThat(sourceFileCaptor.getValue().getKnowledgeBaseId()).isEqualTo(12L);
        assertThat(sourceFileCaptor.getValue().getStatus()).isEqualTo("UPLOADED");

        ArgumentCaptor<AppJob> jobCaptor = ArgumentCaptor.forClass(AppJob.class);
        verify(appJobMapper).insert(jobCaptor.capture());
        assertThat(jobCaptor.getValue().getJobType()).isEqualTo("IMPORT_FILE");
        assertThat(jobCaptor.getValue().getSourceFileId()).isEqualTo(101L);
        assertThat(jobCaptor.getValue().getStatus()).isEqualTo("PENDING");
    }

    @Test
    @DisplayName("普通用户不能上传公共知识库文件")
    void shouldRejectOrdinaryUserUploadingPublicKnowledgeBase() throws Exception {
        KnowledgeBase knowledgeBase = publicKnowledgeBase(12L, 33L);
        when(knowledgeBaseMapper.selectById(12L)).thenReturn(knowledgeBase);
        when(adminRoleService.isAdmin(7L)).thenReturn(false);
        MockMultipartFile file = new MockMultipartFile(
                "file", "a.md", "text/markdown", "hello".getBytes(StandardCharsets.UTF_8));

        assertThatThrownBy(() -> service.upload(12L, 7L, file))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("无权访问");

        verify(fileStorageService, never()).store(any(), any());
        verify(sourceFileMapper, never()).insert(any());
        verify(appJobMapper, never()).insert(any());
    }

    @Test
    @DisplayName("拒绝不支持的文件类型")
    void shouldRejectUnsupportedFileType() {
        KnowledgeBase knowledgeBase = privateKnowledgeBase(12L, 7L, 33L);
        when(knowledgeBaseMapper.selectById(12L)).thenReturn(knowledgeBase);
        MockMultipartFile file = new MockMultipartFile(
                "file", "a.exe", "application/octet-stream", "hello".getBytes(StandardCharsets.UTF_8));

        assertThatThrownBy(() -> service.upload(12L, 7L, file))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("仅支持");
    }

    @Test
    @DisplayName("拒绝超过 20MB 的文件")
    void shouldRejectOversizedFile() {
        KnowledgeBase knowledgeBase = privateKnowledgeBase(12L, 7L, 33L);
        when(knowledgeBaseMapper.selectById(12L)).thenReturn(knowledgeBase);
        MockMultipartFile file = new MockMultipartFile(
                "file", "a.pdf", "application/pdf", new byte[20 * 1024 * 1024 + 1]);

        assertThatThrownBy(() -> service.upload(12L, 7L, file))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("20MB");
    }

    private KnowledgeBase privateKnowledgeBase(Long id, Long ownerUserId, Long positionId) {
        KnowledgeBase knowledgeBase = new KnowledgeBase();
        knowledgeBase.setId(id);
        knowledgeBase.setScope("PRIVATE");
        knowledgeBase.setOwnerUserId(ownerUserId);
        knowledgeBase.setPositionId(positionId);
        knowledgeBase.setStatus("ACTIVE");
        return knowledgeBase;
    }

    private KnowledgeBase publicKnowledgeBase(Long id, Long positionId) {
        KnowledgeBase knowledgeBase = new KnowledgeBase();
        knowledgeBase.setId(id);
        knowledgeBase.setScope("PUBLIC");
        knowledgeBase.setPositionId(positionId);
        knowledgeBase.setStatus("ACTIVE");
        return knowledgeBase;
    }
}
