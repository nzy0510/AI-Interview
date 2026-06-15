package com.interview.controller;

import com.interview.config.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("KnowledgeFileController — 题库文件导入接口")
class KnowledgeFileControllerTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        KnowledgeFileController controller = new KnowledgeFileController();
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("应用内文档上传主流程已禁用")
    void shouldRejectKnowledgeFileUploadInMvp() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "a.md", "text/markdown", "hello".getBytes());

        mockMvc.perform(multipart("/api/knowledge-bases/12/files").file(file))
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.code").value(410))
                .andExpect(jsonPath("$.msg").value("当前版本不支持应用内文档上传，请使用本机题库维护 skill 生成 JSON 导入包"));
    }

    @Test
    @DisplayName("转换后的 Markdown 读取入口已禁用")
    void shouldRejectConvertedMarkdownReadInMvp() throws Exception {
        mockMvc.perform(get("/api/knowledge-files/101/markdown"))
                .andExpect(status().isGone())
                .andExpect(content().string("当前版本不支持应用内文档转换，请使用 JSON 导入包"));
    }
}
