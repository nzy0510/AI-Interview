package com.interview.controller;

import com.interview.service.questionbank.QuestionBankService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("QuestionBankMcpController")
@ExtendWith(MockitoExtension.class)
class QuestionBankMcpControllerTest {

    @Mock
    private QuestionBankService questionBankService;

    @Test
    @DisplayName("initialize returns MCP server metadata without auth")
    void initializeShouldReturnServerMetadata() {
        QuestionBankMcpController controller = new QuestionBankMcpController(questionBankService);
        ResponseEntity<Object> response = controller.handle(Map.of(
                "jsonrpc", "2.0",
                "id", 1,
                "method", "initialize"
        ), new MockHttpServletRequest());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isInstanceOf(Map.class);
        Map<?, ?> body = (Map<?, ?>) response.getBody();
        assertThat(body.get("jsonrpc")).isEqualTo("2.0");
        assertThat(String.valueOf(body.get("result"))).contains("ai-interview-question-bank");
    }

    @Test
    @DisplayName("read tools require MCP read token when configured")
    void readToolsShouldRequireTokenWhenConfigured() {
        QuestionBankMcpController controller = new QuestionBankMcpController(questionBankService);
        ReflectionTestUtils.setField(controller, "readToken", "read-secret");
        ResponseEntity<Object> response = controller.handle(Map.of(
                "jsonrpc", "2.0",
                "id", 2,
                "method", "tools/list"
        ), new MockHttpServletRequest());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(String.valueOf(response.getBody())).contains("MCP read token is invalid");
    }

    @Test
    @DisplayName("read tools pass with configured MCP token")
    void readToolsShouldPassWithToken() {
        QuestionBankMcpController controller = new QuestionBankMcpController(questionBankService);
        ReflectionTestUtils.setField(controller, "readToken", "read-secret");
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-MCP-Token", "read-secret");

        ResponseEntity<Object> response = controller.handle(Map.of(
                "jsonrpc", "2.0",
                "id", 3,
                "method", "tools/list"
        ), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(String.valueOf(response.getBody())).contains("search_interview_atoms");
    }
}
