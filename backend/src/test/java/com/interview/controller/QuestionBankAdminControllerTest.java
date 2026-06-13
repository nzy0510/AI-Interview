package com.interview.controller;

import com.interview.dto.questionbank.QuestionBankPageResponse;
import com.interview.service.AdminGuardService;
import com.interview.service.questionbank.QuestionBankService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
@DisplayName("QuestionBankAdminController 路由")
class QuestionBankAdminControllerTest {

    private MockMvc mockMvc;

    @Mock
    private QuestionBankService questionBankService;

    @Mock
    private AdminGuardService adminGuardService;

    @BeforeEach
    void setUp() {
        QuestionBankAdminController controller = new QuestionBankAdminController(questionBankService, adminGuardService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new com.interview.config.GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("知识原子查询接口应挂在 /api 前缀下")
    void shouldExposeAtomSearchUnderApiPrefix() throws Exception {
        when(questionBankService.listAtoms(any()))
                .thenReturn(QuestionBankPageResponse.of(0, 1, 20, List.of()));

        mockMvc.perform(post("/api/admin/question-bank/atoms/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"category\":\"后端场景题\",\"page\":1,\"size\":20}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        verify(adminGuardService).requireAdmin(any());
        verify(questionBankService).listAtoms(any());
    }

    @Test
    @DisplayName("普通用户被拒绝访问题库管理接口")
    void shouldRejectOrdinaryUser() throws Exception {
        doThrow(new RuntimeException("无权访问管理数据")).when(adminGuardService).requireAdmin(any());

        mockMvc.perform(post("/api/admin/question-bank/atoms/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"page\":1,\"size\":20}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));

        verify(questionBankService, never()).listAtoms(any());
    }
}
