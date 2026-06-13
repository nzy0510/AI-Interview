package com.interview.service.questionbank;

import com.interview.entity.AppJob;
import com.interview.entity.KnowledgeSourceFile;
import com.interview.mapper.KnowledgeSourceFileMapper;
import com.interview.service.AppJobService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("DocumentConversionJobHandler — 文档转换作业")
class DocumentConversionJobHandlerTest {

    @Test
    @DisplayName("转换成功后保存 Markdown 并更新 source file 状态")
    void shouldConvertOriginalFileAndStoreMarkdown() throws Exception {
        KnowledgeSourceFileMapper sourceFileMapper = mock(KnowledgeSourceFileMapper.class);
        FileStorageService fileStorageService = mock(FileStorageService.class);
        DocumentConverterClient converterClient = mock(DocumentConverterClient.class);
        AppJobService appJobService = mock(AppJobService.class);
        KnowledgeSourceFile sourceFile = sourceFile();
        when(sourceFileMapper.selectById(101L)).thenReturn(sourceFile);
        when(converterClient.convertToMarkdown(sourceFile)).thenReturn("# Converted");
        when(fileStorageService.storeText("# Converted", "knowledge/markdown", "doc.md"))
                .thenReturn(new FileStorageService.StoredFile(
                        "knowledge/markdown/doc.md", "doc.md", "text/markdown", 11L, "hash"));
        DocumentConversionJobHandler handler = new DocumentConversionJobHandler(
                sourceFileMapper,
                fileStorageService,
                converterClient,
                appJobService
        );

        handler.handle(job());

        verify(appJobService).updateRunningJob(202L, "worker-1", "CONVERTING", 40);
        verify(appJobService).updateRunningJob(202L, "worker-1", "CONVERTED", 90);
        verify(sourceFileMapper, atLeastOnce()).updateById(any(KnowledgeSourceFile.class));
        assertThat(sourceFile.getMarkdownStorageKey()).isEqualTo("knowledge/markdown/doc.md");
        assertThat(sourceFile.getStatus()).isEqualTo("CONVERTED");
    }

    @Test
    @DisplayName("转换服务不可用时记录可行动的中文错误")
    void shouldShowFriendlyMessageWhenConverterIsUnavailable() throws Exception {
        KnowledgeSourceFileMapper sourceFileMapper = mock(KnowledgeSourceFileMapper.class);
        FileStorageService fileStorageService = mock(FileStorageService.class);
        DocumentConverterClient converterClient = mock(DocumentConverterClient.class);
        AppJobService appJobService = mock(AppJobService.class);
        KnowledgeSourceFile sourceFile = sourceFile();
        when(sourceFileMapper.selectById(101L)).thenReturn(sourceFile);
        when(converterClient.convertToMarkdown(sourceFile)).thenThrow(new RuntimeException(
                "I/O error on POST request for \"http://localhost:8010/convert\": Connection refused: no further information"));
        DocumentConversionJobHandler handler = new DocumentConversionJobHandler(
                sourceFileMapper,
                fileStorageService,
                converterClient,
                appJobService
        );

        assertThatThrownBy(() -> handler.handle(job()))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("文档转换服务暂不可用，请确认 document-converter 已启动");
        assertThat(sourceFile.getStatus()).isEqualTo("FAILED");
        assertThat(sourceFile.getErrorMessage()).isEqualTo("文档转换服务暂不可用，请确认 document-converter 已启动");
    }

    private AppJob job() {
        AppJob job = new AppJob();
        job.setId(202L);
        job.setSourceFileId(101L);
        job.setClaimedBy("worker-1");
        return job;
    }

    private KnowledgeSourceFile sourceFile() {
        KnowledgeSourceFile sourceFile = new KnowledgeSourceFile();
        sourceFile.setId(101L);
        sourceFile.setOriginalFilename("doc.pdf");
        sourceFile.setStorageKey("knowledge/originals/doc.pdf");
        sourceFile.setStatus("UPLOADED");
        return sourceFile;
    }
}
