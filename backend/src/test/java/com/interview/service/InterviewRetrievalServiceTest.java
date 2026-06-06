package com.interview.service;

import com.interview.dto.questionbank.QuestionBankSearchRequest;
import com.interview.dto.questionbank.QuestionBankSearchResponse;
import com.interview.dto.questionbank.QuestionBankSearchResult;
import com.interview.entity.InterviewPhase;
import com.interview.entity.InterviewRecord;
import com.interview.entity.RagRetrievalLog;
import com.interview.mapper.RagRetrievalLogMapper;
import com.interview.mapper.RagRetrievalRequestLogMapper;
import com.interview.service.questionbank.QuestionBankService;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("InterviewRetrievalService — answer and retrieval quality guardrails")
@ExtendWith(MockitoExtension.class)
class InterviewRetrievalServiceTest {

    @Mock
    private QuestionBankService questionBankService;

    @Mock
    private RagRetrievalLogMapper hitLogMapper;

    @Mock
    private RagRetrievalRequestLogMapper requestLogMapper;

    @Mock
    private AppEventService appEventService;

    @Test
    @DisplayName("low-information answer with confident retrieval asks for a remedial follow-up without consuming atoms")
    void shouldAskRemedialFollowUpWithoutConsumingAtomsForLowInfoAnswer() {
        InterviewRetrievalService service = service();
        when(questionBankService.searchWithMetadata(any())).thenReturn(response(result("rag-flow", 0.82)));

        InterviewRetrievalService.TurnRetrieval retrieval = service.retrieve(
                1L, record(), history("请解释 RAG 的检索增强流程"), "我不会",
                InterviewPhase.TECHNICAL, List.of());

        assertThat(retrieval.promptContext())
                .contains("补救追问")
                .contains("RAG context");
        assertThat(retrieval.contextAtomIds()).isEmpty();
    }

    @Test
    @DisplayName("low-information answer with weak retrieval switches topics and does not expose weak context")
    void shouldSwitchTopicWhenLowInfoAnswerHasWeakRetrieval() {
        InterviewRetrievalService service = service();
        when(questionBankService.searchWithMetadata(any())).thenReturn(response(result("weak-hit", 0.41)));

        InterviewRetrievalService.TurnRetrieval retrieval = service.retrieve(
                1L, record(), history("请解释大模型 Agent 的规划流程"), "不太清楚",
                InterviewPhase.TECHNICAL, List.of());

        assertThat(retrieval.promptContext())
                .contains("切换知识点")
                .doesNotContain("RAG context");
        assertThat(retrieval.contextAtomIds()).isEmpty();

        ArgumentCaptor<RagRetrievalLog> hitCaptor = ArgumentCaptor.forClass(RagRetrievalLog.class);
        verify(hitLogMapper).insert(hitCaptor.capture());
        assertThat(hitCaptor.getValue().getContextSelected()).isFalse();
    }

    @Test
    @DisplayName("normal answer with confident retrieval keeps context and consumes selected atoms")
    void shouldKeepContextAndConsumeAtomsForNormalConfidentRetrieval() {
        InterviewRetrievalService service = service();
        when(questionBankService.searchWithMetadata(any())).thenReturn(response(result("rag-flow", 0.83)));

        InterviewRetrievalService.TurnRetrieval retrieval = service.retrieve(
                1L, record(), history("请解释 RAG 的检索增强流程"), "先检索相关文档，再把上下文交给模型生成",
                InterviewPhase.TECHNICAL, List.of());

        assertThat(retrieval.promptContext())
                .contains("RAG context")
                .doesNotContain("补救追问", "切换知识点");
        assertThat(retrieval.contextAtomIds()).containsExactly("rag-flow");
    }

    @Test
    @DisplayName("normal answer with weak retrieval does not depend on weak context or consume atoms")
    void shouldIgnoreWeakRetrievalForNormalAnswer() {
        InterviewRetrievalService service = service();
        when(questionBankService.searchWithMetadata(any())).thenReturn(response(result("weak-hit", 0.42)));

        InterviewRetrievalService.TurnRetrieval retrieval = service.retrieve(
                1L, record(), history("请解释 RAG 的检索增强流程"), "检索会用向量数据库做语义相似度匹配",
                InterviewPhase.TECHNICAL, List.of());

        assertThat(retrieval.promptContext())
                .contains("召回置信度不足")
                .doesNotContain("RAG context");
        assertThat(retrieval.contextAtomIds()).isEmpty();
    }

    @Test
    @DisplayName("consecutive low-information answers switch topics even when retrieval is confident")
    void shouldSwitchTopicAfterConsecutiveLowInfoAnswers() {
        InterviewRetrievalService service = service();
        List<ChatMessage> chatHistory = new ArrayList<>(history("请解释 RAG 的检索增强流程"));
        chatHistory.add(new UserMessage("不知道"));
        chatHistory.add(new AiMessage("那你能说说检索阶段负责什么吗？"));
        when(questionBankService.searchWithMetadata(any())).thenReturn(response(result("rag-flow", 0.84)));

        InterviewRetrievalService.TurnRetrieval retrieval = service.retrieve(
                1L, record(), chatHistory, "还是不会",
                InterviewPhase.TECHNICAL, List.of());

        assertThat(retrieval.promptContext())
                .contains("切换知识点")
                .doesNotContain("RAG context");
        assertThat(retrieval.contextAtomIds()).isEmpty();
    }

    @Test
    @DisplayName("short technical answer expands retrieval candidates without changing prompt consumption")
    void shouldExpandRetrievalLimitForShortTechnicalAnswer() {
        InterviewRetrievalService service = service();
        ReflectionTestUtils.setField(service, "maxRetrievalLimit", 30);
        when(questionBankService.searchWithMetadata(any())).thenReturn(response(result("lora-low-rank", 0.83)));

        service.retrieve(
                1L, record(), history("LoRA 为什么可以减少参数量？"), "低秩矩阵",
                InterviewPhase.TECHNICAL, List.of());

        ArgumentCaptor<QuestionBankSearchRequest> requestCaptor = ArgumentCaptor.forClass(QuestionBankSearchRequest.class);
        verify(questionBankService).searchWithMetadata(requestCaptor.capture());
        assertThat(requestCaptor.getValue().getLimit()).isEqualTo(30);
    }

    @Test
    @DisplayName("low-information answer keeps the default candidate budget")
    void shouldKeepDefaultRetrievalLimitForLowInformationAnswer() {
        InterviewRetrievalService service = service();
        ReflectionTestUtils.setField(service, "maxRetrievalLimit", 30);
        when(questionBankService.searchWithMetadata(any())).thenReturn(response(result("rag-flow", 0.83)));

        service.retrieve(
                1L, record(), history("请解释 RAG 的检索增强流程"), "我不会",
                InterviewPhase.TECHNICAL, List.of());

        ArgumentCaptor<QuestionBankSearchRequest> requestCaptor = ArgumentCaptor.forClass(QuestionBankSearchRequest.class);
        verify(questionBankService).searchWithMetadata(requestCaptor.capture());
        assertThat(requestCaptor.getValue().getLimit()).isEqualTo(20);
    }

    @Test
    @DisplayName("normal single-topic answer keeps the default candidate budget")
    void shouldKeepDefaultRetrievalLimitForNormalSingleTopicAnswer() {
        InterviewRetrievalService service = service();
        ReflectionTestUtils.setField(service, "maxRetrievalLimit", 30);
        when(questionBankService.searchWithMetadata(any())).thenReturn(response(result("rag-flow", 0.83)));

        service.retrieve(
                1L, record(), history("请解释 RAG 的检索增强流程"),
                "先检索相关文档，再把上下文交给模型生成，最后根据上下文回答问题",
                InterviewPhase.TECHNICAL, List.of());

        ArgumentCaptor<QuestionBankSearchRequest> requestCaptor = ArgumentCaptor.forClass(QuestionBankSearchRequest.class);
        verify(questionBankService).searchWithMetadata(requestCaptor.capture());
        assertThat(requestCaptor.getValue().getLimit()).isEqualTo(20);
    }

    @Test
    @DisplayName("mixed technical answer expands retrieval candidates")
    void shouldExpandRetrievalLimitForMixedTechnicalAnswer() {
        InterviewRetrievalService service = service();
        ReflectionTestUtils.setField(service, "maxRetrievalLimit", 30);
        when(questionBankService.searchWithMetadata(any())).thenReturn(response(result("agent-rag", 0.83)));

        service.retrieve(
                1L, record(), history("你做过哪些大模型应用？"),
                "项目里同时用了 RAG、Embedding、向量库和 Agent 工具调用，但细节有点混在一起",
                InterviewPhase.TECHNICAL, List.of());

        ArgumentCaptor<QuestionBankSearchRequest> requestCaptor = ArgumentCaptor.forClass(QuestionBankSearchRequest.class);
        verify(questionBankService).searchWithMetadata(requestCaptor.capture());
        assertThat(requestCaptor.getValue().getLimit()).isEqualTo(30);
    }

    private InterviewRetrievalService service() {
        return new InterviewRetrievalService(
                questionBankService, hitLogMapper, requestLogMapper, appEventService);
    }

    private InterviewRecord record() {
        InterviewRecord record = new InterviewRecord();
        record.setId(10L);
        record.setPosition("AI大模型");
        return record;
    }

    private List<ChatMessage> history(String previousQuestion) {
        return new ArrayList<>(List.of(new AiMessage(previousQuestion)));
    }

    private QuestionBankSearchResponse response(QuestionBankSearchResult result) {
        return QuestionBankSearchResponse.builder()
                .results(List.of(result))
                .strategy("QDRANT_VECTOR")
                .build();
    }

    private QuestionBankSearchResult result(String atomId, double score) {
        return QuestionBankSearchResult.builder()
                .atomId(atomId)
                .category("AI大模型")
                .score(score)
                .promptContext("RAG context")
                .build();
    }
}
