package com.interview.service.questionbank;

import com.interview.service.UserLlmModelFactory;
import com.interview.service.UserLlmRuntimeConfig;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.output.Response;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("DefaultKnowledgeAtomAiClient — 知识原子生成模型调用")
class DefaultKnowledgeAtomAiClientTest {

    @Test
    @DisplayName("批量原子生成使用专用长超时模型")
    void shouldUseLongTimeoutForAtomGeneration() {
        UserLlmModelFactory modelFactory = mock(UserLlmModelFactory.class);
        OpenAiChatModel model = mock(OpenAiChatModel.class);
        UserLlmRuntimeConfig config = runtimeConfig();
        when(modelFactory.createChatModel(config, Duration.ofMinutes(5))).thenReturn(model);
        when(model.generate(anyList())).thenReturn(Response.from(AiMessage.from("""
                {"atomLimitReached":false,"atoms":[]}
                """)));
        DefaultKnowledgeAtomAiClient client = new DefaultKnowledgeAtomAiClient(modelFactory);

        KnowledgeAtomDraftBundle bundle = client.generateReviewedAtoms(config, "# Java");

        assertThat(bundle.atoms()).isEmpty();
        verify(modelFactory).createChatModel(config, Duration.ofMinutes(5));
    }

    @Test
    @DisplayName("生成提示要求模型尽量单次完整覆盖 Markdown")
    void shouldAskModelToCompleteExtractionInOneCallWhenPossible() {
        UserLlmModelFactory modelFactory = mock(UserLlmModelFactory.class);
        OpenAiChatModel model = mock(OpenAiChatModel.class);
        UserLlmRuntimeConfig config = runtimeConfig();
        when(modelFactory.createChatModel(config, Duration.ofMinutes(5))).thenReturn(model);
        when(model.generate(anyList())).thenReturn(Response.from(AiMessage.from("""
                {"atomLimitReached":false,"atoms":[]}
                """)));
        DefaultKnowledgeAtomAiClient client = new DefaultKnowledgeAtomAiClient(modelFactory);

        client.generateReviewedAtoms(config, "# Java");

        @SuppressWarnings("unchecked")
        var messagesCaptor = forClass(List.class);
        verify(model).generate(messagesCaptor.capture());
        assertThat(messagesCaptor.getValue().get(0).toString()).contains("尽量在单次调用内完整覆盖");
    }

    private UserLlmRuntimeConfig runtimeConfig() {
        return new UserLlmRuntimeConfig(1L, 7L, "custom", "Custom", "https://llm.example/v1",
                "test-model", "sk-test", 0.1);
    }
}
