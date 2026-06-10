package com.interview.service;

import com.interview.dto.llm.LlmConfigRequest;
import com.interview.dto.llm.LlmConfigResponse;
import com.interview.entity.UserLlmConfig;
import com.interview.exception.LlmProviderRequiredException;
import com.interview.mapper.UserLlmConfigMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("UserLlmConfigService — 用户大模型配置")
@ExtendWith(MockitoExtension.class)
class UserLlmConfigServiceTest {

    @Mock
    private UserLlmConfigMapper mapper;

    @Mock
    private UserLlmModelFactory modelFactory;

    @Test
    @DisplayName("新增配置时加密存储 API Key 且响应只返回脱敏摘要")
    void shouldEncryptApiKeyAndReturnOnlyHint() {
        ApiKeyCryptoService cryptoService = new ApiKeyCryptoService("0123456789abcdef0123456789abcdef");
        UserLlmConfigService service = new UserLlmConfigService(mapper, cryptoService, modelFactory);
        doAnswer(invocation -> {
            UserLlmConfig config = invocation.getArgument(0);
            config.setId(42L);
            return 1;
        }).when(mapper).insert(any(UserLlmConfig.class));

        LlmConfigRequest request = new LlmConfigRequest();
        request.setProvider("deepseek");
        request.setDisplayName("DeepSeek");
        request.setBaseUrl("https://api.deepseek.com/v1");
        request.setModelName("deepseek-chat");
        request.setApiKey("sk-test-secret-7890");
        request.setTemperature(0.7);
        request.setActive(true);

        LlmConfigResponse response = service.create(7L, request);

        ArgumentCaptor<UserLlmConfig> captor = ArgumentCaptor.forClass(UserLlmConfig.class);
        verify(mapper).insert(captor.capture());
        UserLlmConfig saved = captor.getValue();
        assertThat(saved.getEncryptedApiKey()).isNotBlank();
        assertThat(saved.getEncryptedApiKey()).doesNotContain("sk-test-secret-7890");
        assertThat(cryptoService.decrypt(saved.getEncryptedApiKey())).isEqualTo("sk-test-secret-7890");
        assertThat(response.getApiKeyHint()).isEqualTo("sk...7890");
        assertThat(response).hasNoNullFieldsOrPropertiesExcept("lastTestStatus", "lastTestMessage", "lastTestTime");
    }

    @Test
    @DisplayName("没有启用配置时明确要求用户先配置")
    void shouldRequireActiveProvider() {
        ApiKeyCryptoService cryptoService = new ApiKeyCryptoService("0123456789abcdef0123456789abcdef");
        UserLlmConfigService service = new UserLlmConfigService(mapper, cryptoService, modelFactory);
        when(mapper.selectOne(any())).thenReturn(null);

        assertThatThrownBy(() -> service.requireActiveRuntimeConfig(7L))
                .isInstanceOf(LlmProviderRequiredException.class)
                .hasMessageContaining("请先配置大模型 API");
    }
}
