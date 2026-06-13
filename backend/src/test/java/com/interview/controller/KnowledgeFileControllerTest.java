package com.interview.controller;

import com.interview.config.GlobalExceptionHandler;
import com.interview.service.RequestUserResolver;
import com.interview.service.questionbank.KnowledgeFileImportService;
import com.interview.service.questionbank.KnowledgeFileReadService;
import com.interview.service.questionbank.KnowledgeFileUploadResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
@DisplayName("KnowledgeFileController — 题库文件导入接口")
class KnowledgeFileControllerTest {

    private MockMvc mockMvc;

    @Mock
    private KnowledgeFileImportService importService;

    @Mock
    private KnowledgeFileReadService readService;

    @Mock
    private RequestUserResolver requestUserResolver;

    @BeforeEach
    void setUp() {
        KnowledgeFileController controller = new KnowledgeFileController(
                importService,
                readService,
                requestUserResolver
        );
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("上传文件时使用当前登录用户并返回 sourceFileId 与 jobId")
    void shouldUploadKnowledgeFile() throws Exception {
        when(requestUserResolver.resolveUserId(any(HttpServletRequest.class))).thenReturn(7L);
        MockMultipartFile file = new MockMultipartFile("file", "a.md", "text/markdown", "hello".getBytes());
        when(importService.upload(12L, 7L, file))
                .thenReturn(new KnowledgeFileUploadResponse(101L, 202L, "UPLOADED"));

        mockMvc.perform(multipart("/api/knowledge-bases/12/files").file(file))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.sourceFileId").value(101))
                .andExpect(jsonPath("$.data.jobId").value(202));

        verify(importService).upload(12L, 7L, file);
    }

    @Test
    @DisplayName("读取 Markdown 时校验当前用户可见性")
    void shouldReadConvertedMarkdownWithCurrentUser() throws Exception {
        when(requestUserResolver.resolveUserId(any(HttpServletRequest.class))).thenReturn(7L);
        when(readService.readMarkdown(101L, 7L)).thenReturn("# Converted");

        mockMvc.perform(get("/api/knowledge-files/101/markdown"))
                .andExpect(status().isOk())
                .andExpect(content().string("# Converted"));

        verify(readService).readMarkdown(101L, 7L);
    }
}
