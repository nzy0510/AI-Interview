package com.interview.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.interview.config.GlobalExceptionHandler;
import com.interview.service.RequestUserResolver;
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
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
                20L, "PRIVATE", 7L, "算法工程师", "", "ACTIVE", true, null);
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
    @DisplayName("归档私有岗位使用当前登录用户")
    void shouldArchivePrivatePositionForCurrentUser() throws Exception {
        when(requestUserResolver.resolveUserId(any(HttpServletRequest.class))).thenReturn(7L);

        mockMvc.perform(post("/api/knowledge-workspace/positions/20/archive"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        verify(workspaceService).archivePrivatePosition(7L, 20L);
    }
}
