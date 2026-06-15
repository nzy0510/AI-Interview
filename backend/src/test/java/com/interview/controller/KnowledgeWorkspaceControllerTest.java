package com.interview.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.interview.config.GlobalExceptionHandler;
import com.interview.service.RequestUserResolver;
import com.interview.dto.questionbank.QuestionBankAtomQueryRequest;
import com.interview.dto.questionbank.QuestionBankBulkAtomRequest;
import com.interview.dto.questionbank.QuestionBankImportPreviewResponse;
import com.interview.dto.questionbank.QuestionBankImportRequest;
import com.interview.dto.questionbank.QuestionBankImportResult;
import com.interview.dto.questionbank.QuestionBankPageResponse;
import com.interview.service.questionbank.KnowledgePositionCreateRequest;
import com.interview.service.questionbank.KnowledgePositionResponse;
import com.interview.service.questionbank.KnowledgeWorkspaceResponse;
import com.interview.service.questionbank.KnowledgeWorkspaceService;
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
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
@DisplayName("KnowledgeWorkspaceController — 知识库工作台接口")
class KnowledgeWorkspaceControllerTest {

    private MockMvc mockMvc;

    @Mock
    private KnowledgeWorkspaceService workspaceService;

    @Mock
    private RequestUserResolver requestUserResolver;

    @BeforeEach
    void setUp() {
        KnowledgeWorkspaceController controller = new KnowledgeWorkspaceController(workspaceService, requestUserResolver);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("岗位列表使用当前登录用户")
    void shouldListWorkspaceForCurrentUser() throws Exception {
        when(requestUserResolver.resolveUserId(any(HttpServletRequest.class))).thenReturn(7L);
        when(workspaceService.listWorkspace(7L)).thenReturn(new KnowledgeWorkspaceResponse(List.of()));

        mockMvc.perform(get("/api/knowledge-workspace/positions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        verify(workspaceService).listWorkspace(7L);
    }

    @Test
    @DisplayName("创建私有岗位使用当前登录用户")
    void shouldCreatePrivatePositionForCurrentUser() throws Exception {
        when(requestUserResolver.resolveUserId(any(HttpServletRequest.class))).thenReturn(7L);
        KnowledgePositionResponse response = new KnowledgePositionResponse(
                20L, "PRIVATE", 7L, "算法工程师", "", "ACTIVE", true,
                true, true, false, false, true, null);
        when(workspaceService.createPrivatePosition(any(), any())).thenReturn(response);

        mockMvc.perform(post("/api/knowledge-workspace/positions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(new ObjectMapper().writeValueAsString(new KnowledgePositionCreateRequest("算法工程师", ""))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(20))
                .andExpect(jsonPath("$.data.editable").value(true));

        verify(workspaceService).createPrivatePosition(any(), any());
    }

    @Test
    @DisplayName("删除私有岗位使用当前登录用户")
    void shouldDeletePrivatePositionForCurrentUser() throws Exception {
        when(requestUserResolver.resolveUserId(any(HttpServletRequest.class))).thenReturn(7L);

        mockMvc.perform(delete("/api/knowledge-workspace/positions/20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        verify(workspaceService).deletePrivatePosition(7L, 20L);
    }

    @Test
    @DisplayName("校验知识库导入包使用当前登录用户")
    void shouldPreviewImportPackageForCurrentKnowledgeBase() throws Exception {
        when(requestUserResolver.resolveUserId(any(HttpServletRequest.class))).thenReturn(7L);
        QuestionBankImportPreviewResponse response = new QuestionBankImportPreviewResponse();
        response.setBatchId("qb-private");
        when(workspaceService.previewImportPackage(any(), any(), any())).thenReturn(response);

        mockMvc.perform(post("/api/knowledge-workspace/knowledge-bases/30/import/validate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(new ObjectMapper().writeValueAsString(new QuestionBankImportRequest())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.batchId").value("qb-private"));

        verify(workspaceService).previewImportPackage(any(), any(), any());
    }

    @Test
    @DisplayName("导入知识库导入包使用当前登录用户")
    void shouldImportPackageForCurrentKnowledgeBase() throws Exception {
        when(requestUserResolver.resolveUserId(any(HttpServletRequest.class))).thenReturn(7L);
        when(workspaceService.importPackage(any(), any(), any())).thenReturn(QuestionBankImportResult.builder()
                .batchId("qb-private")
                .mode("DRAFT")
                .imported(1)
                .build());

        mockMvc.perform(post("/api/knowledge-workspace/knowledge-bases/30/import")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(new ObjectMapper().writeValueAsString(new QuestionBankImportRequest())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.mode").value("DRAFT"))
                .andExpect(jsonPath("$.data.imported").value(1));

        verify(workspaceService).importPackage(any(), any(), any());
    }

    @Test
    @DisplayName("发布知识库原子使用当前登录用户和当前知识库")
    void shouldPublishKnowledgeBaseAtomsForCurrentUser() throws Exception {
        when(requestUserResolver.resolveUserId(any(HttpServletRequest.class))).thenReturn(7L);
        when(workspaceService.publishAtoms(any(), any(), any())).thenReturn(Map.of("published", 1, "synced", 1));
        QuestionBankBulkAtomRequest request = new QuestionBankBulkAtomRequest();
        request.setAtomIds(List.of("atom-1"));

        mockMvc.perform(post("/api/knowledge-workspace/knowledge-bases/30/atoms/publish")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(new ObjectMapper().writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.published").value(1));

        verify(workspaceService).publishAtoms(any(), any(), any());
    }

    @Test
    @DisplayName("查询知识库原子使用当前登录用户和当前知识库")
    void shouldListKnowledgeBaseAtomsForCurrentUser() throws Exception {
        when(requestUserResolver.resolveUserId(any(HttpServletRequest.class))).thenReturn(7L);
        when(workspaceService.listAtoms(any(), any(), any())).thenReturn(QuestionBankPageResponse.of(0, 1, 20, List.of()));

        mockMvc.perform(post("/api/knowledge-workspace/knowledge-bases/30/atoms/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(new ObjectMapper().writeValueAsString(new QuestionBankAtomQueryRequest())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(0));

        verify(workspaceService).listAtoms(any(), any(), any());
    }

    @Test
    @DisplayName("一键发布全部草稿使用当前登录用户和当前知识库")
    void shouldPublishAllDraftsForCurrentUser() throws Exception {
        when(requestUserResolver.resolveUserId(any(HttpServletRequest.class))).thenReturn(7L);
        when(workspaceService.publishAllDrafts(any(), any())).thenReturn(Map.of("published", 3, "synced", 3, "failed", 0));

        mockMvc.perform(post("/api/knowledge-workspace/knowledge-bases/30/atoms/publish-drafts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.published").value(3));

        verify(workspaceService).publishAllDrafts(7L, 30L);
    }
}
