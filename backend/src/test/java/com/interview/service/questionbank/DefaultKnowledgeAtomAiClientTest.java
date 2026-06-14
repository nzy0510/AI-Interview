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
import static org.mockito.Mockito.times;
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
    @DisplayName("生成提示要求模型只基于当前 Markdown 分块提取原子")
    void shouldAskModelToExtractAtomsFromCurrentMarkdownChunk() {
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
        assertThat(messagesCaptor.getValue().get(0).toString())
                .contains("当前 Markdown 已按标题、段落、列表和代码块边界分块")
                .contains("请只基于当前分块提取");
    }

    @Test
    @DisplayName("无标题长 Markdown 退化分块生成并聚合原子")
    void shouldChunkLongMarkdownAndMergeGeneratedAtoms() {
        UserLlmModelFactory modelFactory = mock(UserLlmModelFactory.class);
        OpenAiChatModel model = mock(OpenAiChatModel.class);
        UserLlmRuntimeConfig config = runtimeConfig();
        when(modelFactory.createChatModel(config, Duration.ofMinutes(5))).thenReturn(model);
        when(model.generate(anyList())).thenReturn(
                Response.from(AiMessage.from("""
                        {"atomLimitReached":false,"atoms":[{"subject":"chunk 1","category":"Java","difficulty":"MEDIUM","tags":[],"principles":"p1","followUpPaths":[],"review":{"status":"PASS","reason":"ok","confidence":0.9}}]}
                        """)),
                Response.from(AiMessage.from("""
                        {"atomLimitReached":false,"atoms":[{"subject":"chunk 2","category":"Java","difficulty":"MEDIUM","tags":[],"principles":"p2","followUpPaths":[],"review":{"status":"PASS","reason":"ok","confidence":0.9}}]}
                        """)),
                Response.from(AiMessage.from("""
                        {"atomLimitReached":false,"atoms":[{"subject":"chunk 3","category":"Java","difficulty":"MEDIUM","tags":[],"principles":"p3","followUpPaths":[],"review":{"status":"PASS","reason":"ok","confidence":0.9}}]}
                        """))
        );
        DefaultKnowledgeAtomAiClient client = new DefaultKnowledgeAtomAiClient(modelFactory);

        KnowledgeAtomDraftBundle bundle = client.generateReviewedAtoms(config, "Java ".repeat(5200));

        assertThat(bundle.atoms())
                .extracting(KnowledgeAtomDraft::subject)
                .containsExactly("chunk 1", "chunk 2", "chunk 3");
        assertThat(bundle.atomLimitReached()).isFalse();
        verify(model, times(3)).generate(anyList());
    }

    @Test
    @DisplayName("Markdown 分块优先保持标题章节完整，避免把下一章节切入上一块")
    void shouldKeepMarkdownSectionsTogetherWhenChunkingLongDocuments() {
        UserLlmModelFactory modelFactory = mock(UserLlmModelFactory.class);
        OpenAiChatModel model = mock(OpenAiChatModel.class);
        UserLlmRuntimeConfig config = runtimeConfig();
        when(modelFactory.createChatModel(config, Duration.ofMinutes(5))).thenReturn(model);
        when(model.generate(anyList())).thenReturn(
                Response.from(AiMessage.from("{\"atomLimitReached\":false,\"atoms\":[]}")),
                Response.from(AiMessage.from("{\"atomLimitReached\":false,\"atoms\":[]}"))
        );
        DefaultKnowledgeAtomAiClient client = new DefaultKnowledgeAtomAiClient(modelFactory);
        String markdown = """
                # HashMap 原理
                %s
                # ConcurrentHashMap 原理
                %s
                """.formatted("HashMap 扩容、扰动函数、链表转红黑树。\n".repeat(350),
                "ConcurrentHashMap 分段锁、CAS、sizeCtl。\n".repeat(300));

        client.generateReviewedAtoms(config, markdown);

        @SuppressWarnings("unchecked")
        var messagesCaptor = forClass(List.class);
        verify(model, times(2)).generate(messagesCaptor.capture());
        String firstChunk = messagesCaptor.getAllValues().get(0).get(1).toString();
        String secondChunk = messagesCaptor.getAllValues().get(1).get(1).toString();
        assertThat(firstChunk)
                .contains("# HashMap 原理")
                .doesNotContain("# ConcurrentHashMap 原理");
        assertThat(secondChunk).contains("# ConcurrentHashMap 原理");
    }

    private UserLlmRuntimeConfig runtimeConfig() {
        return new UserLlmRuntimeConfig(1L, 7L, "custom", "Custom", "https://llm.example/v1",
                "test-model", "sk-test", 0.1);
    }
}
