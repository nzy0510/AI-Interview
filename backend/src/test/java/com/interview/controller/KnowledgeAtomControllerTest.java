package com.interview.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.interview.config.GlobalExceptionHandler;
import com.interview.service.RequestUserResolver;
import com.interview.service.questionbank.KnowledgeAtomGenerationResult;
import com.interview.service.questionbank.KnowledgeAtomBulkPublishResult;
import com.interview.service.questionbank.KnowledgeAtomJobService;
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
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
@DisplayName("KnowledgeAtomController — 文件原子生成与发布接口")
class KnowledgeAtomControllerTest {

    private MockMvc mockMvc;

    @Mock
    private KnowledgeAtomWorkflowService workflowService;

    @Mock
    private KnowledgeAtomJobService jobService;

    @Mock
    private RequestUserResolver requestUserResolver;

    @BeforeEach
    void setUp() {
        KnowledgeAtomController controller = new KnowledgeAtomController(workflowService, jobService, requestUserResolver);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("触发文件原子生成作业时使用当前登录用户")
    void shouldCreateGenerationJobForCurrentUser() throws Exception {
        when(requestUserResolver.resolveUserId(any(HttpServletRequest.class))).thenReturn(7L);
        when(jobService.createGenerationJob(10L, 7L))
                .thenReturn(new KnowledgeAtomGenerationResult(10L, 0, 0, false));

        mockMvc.perform(post("/api/knowledge-files/10/atoms/generate"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.sourceFileId").value(10));

        verify(jobService).createGenerationJob(10L, 7L);
    }

    @Test
    @DisplayName("列出文件下原子草稿")
    void shouldListAtomsForSourceFile() throws Exception {
        when(requestUserResolver.resolveUserId(any(HttpServletRequest.class))).thenReturn(7L);
        when(workflowService.listAtomsForSourceFile(10L, 7L)).thenReturn(List.of(atomResponse()));

        mockMvc.perform(get("/api/knowledge-files/10/atoms"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").value(5))
                .andExpect(jsonPath("$.data[0].reviewStatus").value("PASS"));

        verify(workflowService).listAtomsForSourceFile(10L, 7L);
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
    @DisplayName("批量发布文件下可发布原子时使用当前登录用户")
    void shouldBulkPublishSourceFileAtomsForCurrentUser() throws Exception {
        when(requestUserResolver.resolveUserId(any(HttpServletRequest.class))).thenReturn(7L);
        when(workflowService.publishAtomsForSourceFile(10L, 7L))
                .thenReturn(new KnowledgeAtomBulkPublishResult(10L, 4, 2, 2, 0, 2));

        mockMvc.perform(post("/api/knowledge-files/10/atoms/publish"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.sourceFileId").value(10))
                .andExpect(jsonPath("$.data.matched").value(4))
                .andExpect(jsonPath("$.data.published").value(2))
                .andExpect(jsonPath("$.data.skipped").value(2));

        verify(workflowService).publishAtomsForSourceFile(10L, 7L);
    }

    private KnowledgeAtomResponse atomResponse() {
        return new KnowledgeAtomResponse(5L, "atom-5", "考点", "Java", "MEDIUM", "[]",
                "答案", null, "[]", "DRAFT", "SKIPPED", "PASS",
                "ok", 0.9, null, "DRAFT", 1, null, null);
    }
}
