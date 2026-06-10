package com.interview.controller;

import com.interview.common.Result;
import com.interview.dto.llm.LlmConfigRequest;
import com.interview.dto.llm.LlmConfigResponse;
import com.interview.service.UserLlmConfigService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("UserLlmConfigController — 用户大模型配置 API")
class UserLlmConfigControllerTest {

    @Test
    @DisplayName("配置列表只返回脱敏 key 摘要")
    void shouldReturnMaskedConfigList() {
        UserLlmConfigController controller = new UserLlmConfigController();
        UserLlmConfigService service = mock(UserLlmConfigService.class);
        ReflectionTestUtils.setField(controller, "userLlmConfigService", service);

        LlmConfigResponse item = new LlmConfigResponse();
        item.setId(1L);
        item.setProvider("deepseek");
        item.setDisplayName("DeepSeek");
        item.setBaseUrl("https://api.deepseek.com/v1");
        item.setModelName("deepseek-chat");
        item.setApiKeyHint("sk...7890");
        item.setActive(true);
        when(service.list(7L)).thenReturn(List.of(item));

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute("currentUserId", 7L);

        Result<List<LlmConfigResponse>> result = controller.list(request);

        assertThat(result.getData()).hasSize(1);
        assertThat(result.getData().get(0).getApiKeyHint()).isEqualTo("sk...7890");
        assertThat(result.getData().toString()).doesNotContain("sk-test-secret");
    }

    @Test
    @DisplayName("新增配置使用当前登录用户")
    void shouldCreateConfigForCurrentUser() {
        UserLlmConfigController controller = new UserLlmConfigController();
        UserLlmConfigService service = mock(UserLlmConfigService.class);
        ReflectionTestUtils.setField(controller, "userLlmConfigService", service);

        LlmConfigRequest body = new LlmConfigRequest();
        LlmConfigResponse response = new LlmConfigResponse();
        response.setId(8L);
        when(service.create(7L, body)).thenReturn(response);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute("currentUserId", 7L);

        Result<LlmConfigResponse> result = controller.create(body, request);

        assertThat(result.getData().getId()).isEqualTo(8L);
    }
}
