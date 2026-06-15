package com.interview.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.interview.config.GlobalExceptionHandler;
import com.interview.service.RequestUserResolver;
import com.interview.service.questionbank.KnowledgeAtomPatch;
import com.interview.service.questionbank.KnowledgeAtomResponse;
import com.interview.service.questionbank.KnowledgeAtomWorkflowService;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
@DisplayName("KnowledgeAtomController — 知识原子维护接口")
class KnowledgeAtomControllerTest {

    private MockMvc mockMvc;

    @Mock
    private KnowledgeAtomWorkflowService workflowService;

    @Mock
    private RequestUserResolver requestUserResolver;

    @BeforeEach
    void setUp() {
        KnowledgeAtomController controller = new KnowledgeAtomController(workflowService, requestUserResolver);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("应用内 LLM 原子生成入口已禁用")
    void shouldRejectSourceFileAtomGenerationInMvp() throws Exception {
        mockMvc.perform(post("/api/knowledge-files/10/atoms/generate"))
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.code").value(410));

    }

    @Test
    @DisplayName("源文件原子列表入口已退出 MVP 主流程")
    void shouldRejectSourceFileAtomListingInMvp() throws Exception {
        mockMvc.perform(get("/api/knowledge-files/10/atoms"))
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.code").value(410));

        verifyNoInteractions(workflowService);
    }

    @Test
    @DisplayName("应用建议补丁、编辑、发布均使用当前登录用户")
    void shouldPatchEditAndPublishAtomForCurrentUser() throws Exception {
        when(requestUserResolver.resolveUserId(any(HttpServletRequest.class))).thenReturn(7L);
        when(workflowService.acceptSuggestedPatch(5L, 7L)).thenReturn(atomResponse());
        when(workflowService.updateAtom(any(), any(), any())).thenReturn(atomResponse());
        when(workflowService.publishAtom(5L, 7L)).thenReturn(atomResponse());

        mockMvc.perform(post("/api/knowledge-atoms/5/accept-patch"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(5));

        mockMvc.perform(put("/api/knowledge-atoms/5")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(new ObjectMapper().writeValueAsString(
                                new KnowledgeAtomPatch("新考点", null, null, null, "新答案", null, null))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(5));

        mockMvc.perform(post("/api/knowledge-atoms/5/publish"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(5));

        verify(workflowService).acceptSuggestedPatch(5L, 7L);
        verify(workflowService).updateAtom(any(), any(), any());
        verify(workflowService).publishAtom(5L, 7L);
    }

    @Test
    @DisplayName("源文件批量发布入口已退出 MVP 主流程")
    void shouldRejectSourceFileAtomBulkPublishInMvp() throws Exception {
        mockMvc.perform(post("/api/knowledge-files/10/atoms/publish"))
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.code").value(410));

        verifyNoInteractions(workflowService);
    }

    private KnowledgeAtomResponse atomResponse() {
        return new KnowledgeAtomResponse(5L, "atom-5", "考点", "Java", "MEDIUM", "[]",
                "答案", null, "[]", "DRAFT", "SKIPPED", "PASS",
                "ok", 0.9, null, "DRAFT", 1, null, null);
    }
}
