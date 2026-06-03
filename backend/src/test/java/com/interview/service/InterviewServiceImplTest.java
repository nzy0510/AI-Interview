package com.interview.service;

import com.interview.entity.InterviewPhase;
import com.interview.entity.InterviewRecord;
import com.interview.entity.RagRetrievalLog;
import com.interview.entity.RagRetrievalRequestLog;
import com.interview.dto.questionbank.QuestionBankSearchRequest;
import com.interview.dto.questionbank.QuestionBankSearchResponse;
import com.interview.dto.questionbank.QuestionBankSearchResult;
import com.interview.mapper.InterviewRecordMapper;
import com.interview.mapper.RagRetrievalLogMapper;
import com.interview.mapper.RagRetrievalRequestLogMapper;
import com.interview.service.impl.InterviewServiceImpl;
import com.interview.service.questionbank.QuestionBankService;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.StreamingResponseHandler;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import dev.langchain4j.model.output.Response;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@DisplayName("InterviewService — 面试记录归属校验")
@ExtendWith(MockitoExtension.class)
class InterviewServiceImplTest {

    @Mock
    private InterviewRecordMapper interviewRecordMapper;

    @Mock
    private SessionStore sessionStore;

    @Mock
    private OpenAiStreamingChatModel streamingChatModel;

    @Mock
    private QuestionBankService questionBankService;

    @Mock
    private RagRetrievalLogMapper ragRetrievalLogMapper;

    @Mock
    private RagRetrievalRequestLogMapper ragRetrievalRequestLogMapper;

    @Mock
    private InterviewTurnPlanner interviewTurnPlanner;

    @InjectMocks
    private InterviewServiceImpl interviewService;

    @Test
    @DisplayName("历史详情只返回当前用户拥有的记录")
    void shouldReturnOwnedHistoryDetail() {
        InterviewRecord record = new InterviewRecord();
        record.setId(10L);
        record.setUserId(1L);

        when(interviewRecordMapper.selectOne(any())).thenReturn(record);

        InterviewRecord detail = interviewService.getHistoryDetail(1L, 10L);

        assertThat(detail).isSameAs(record);
    }

    @Test
    @DisplayName("历史详情不属于当前用户时拒绝访问")
    void shouldRejectForeignHistoryDetail() {
        when(interviewRecordMapper.selectOne(any())).thenReturn(null);

        assertThatThrownBy(() -> interviewService.getHistoryDetail(1L, 99L))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("无权访问");
    }

    @Test
    @DisplayName("结束面试不属于当前用户时拒绝访问")
    void shouldRejectFinishingForeignInterview() {
        when(interviewRecordMapper.selectOne(any())).thenReturn(null);

        assertThatThrownBy(() -> interviewService.endInterview(1L, 99L, 0, null))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("无权访问");
    }

    @Test
    @DisplayName("开始面试时持久化准备页难度和重点能力")
    void shouldPersistSetupSelectionsWhenStartingInterview() {
        interviewService.startInterview(
                1L,
                "Java 后端开发",
                "video",
                null,
                "senior",
                List.of("projects", "systemDesign"));

        ArgumentCaptor<InterviewRecord> captor = ArgumentCaptor.forClass(InterviewRecord.class);
        verify(interviewRecordMapper).insert(captor.capture());
        InterviewRecord saved = captor.getValue();
        assertThat(saved.getUserId()).isEqualTo(1L);
        assertThat(saved.getPosition()).isEqualTo("Java 后端开发");
        assertThat(saved.getInterviewMode()).isEqualTo("video");
        assertThat(saved.getDifficultyLevel()).isEqualTo("senior");
        assertThat(saved.getFocusAreas()).contains("projects", "systemDesign");
    }

    @Test
    @DisplayName("题库检索失败时仍继续请求 AI 输出")
    void shouldContinueStreamingWhenQuestionBankSearchFails() {
        InterviewRecord record = new InterviewRecord();
        record.setId(20L);
        record.setUserId(1L);
        record.setPosition("测试开发");
        record.setPhase(InterviewPhase.OPENING.name());

        when(interviewRecordMapper.selectOne(any())).thenReturn(record);
        when(sessionStore.load(20L)).thenReturn(new ArrayList<>());
        when(sessionStore.loadUsedAtoms(20L)).thenReturn(List.of());
        when(sessionStore.loadTailoredQuestions(20L)).thenReturn(List.of());
        when(interviewTurnPlanner.determineNextPhase(any(), anyList()))
                .thenReturn(InterviewPhase.TECHNICAL);
        when(questionBankService.searchWithMetadata(any()))
                .thenThrow(new IllegalArgumentException(
                        "调用 https://example.com/search 失败: api_key=raw-key token: raw-token"));
        when(interviewTurnPlanner.plan(any(), anyList(), any(), any()))
                .thenReturn(new InterviewTurnPlanner.InterviewTurnPlan(InterviewPhase.OPENING, "coordinator"));
        doAnswer(invocation -> {
            StreamingResponseHandler<AiMessage> handler = invocation.getArgument(1);
            handler.onNext("你好");
            handler.onComplete(Response.from(new AiMessage("你好")));
            return null;
        }).when(streamingChatModel).generate(anyList(), any());

        SseEmitter emitter = interviewService.chatStream(1L, 20L, "你好");

        assertThat(emitter).isNotNull();
        verify(questionBankService).searchWithMetadata(any());
        verify(streamingChatModel).generate(anyList(), any());

        ArgumentCaptor<RagRetrievalRequestLog> requestLogCaptor =
                ArgumentCaptor.forClass(RagRetrievalRequestLog.class);
        verify(ragRetrievalRequestLogMapper).insert(requestLogCaptor.capture());
        RagRetrievalRequestLog requestLog = requestLogCaptor.getValue();
        assertThat(requestLog.getTurnIndex()).isEqualTo(1);
        assertThat(requestLog.getCandidateCount()).isZero();
        assertThat(requestLog.getRetrievalStrategy()).isEqualTo("FAILED");
        assertThat(requestLog.getStatus()).isEqualTo("FAILED");
        assertThat(requestLog.getErrorMessage())
                .doesNotContain("raw-key", "raw-token", "https://example.com/search")
                .contains("[REDACTED]", "[URL]");
    }

    @Test
    @DisplayName("HR 阶段题库检索只使用 HR 软技能分类")
    void shouldRouteHrStageSearchToHrSoftSkillCategory() {
        InterviewRecord record = new InterviewRecord();
        record.setId(21L);
        record.setUserId(1L);
        record.setPosition("Java 后端开发");
        record.setPhase(InterviewPhase.TECHNICAL.name());

        when(interviewRecordMapper.selectOne(any())).thenReturn(record);
        when(sessionStore.load(21L)).thenReturn(new ArrayList<>());
        when(sessionStore.loadUsedAtoms(21L)).thenReturn(List.of("used-atom"));
        when(sessionStore.loadTailoredQuestions(21L)).thenReturn(List.of());
        when(interviewTurnPlanner.determineNextPhase(any(), anyList()))
                .thenReturn(InterviewPhase.HR);
        when(questionBankService.searchWithMetadata(any())).thenReturn(QuestionBankSearchResponse.builder()
                .results(List.of())
                .strategy("MYSQL_FALLBACK")
                .build());
        when(interviewTurnPlanner.plan(any(), anyList(), any(), any()))
                .thenReturn(new InterviewTurnPlanner.InterviewTurnPlan(InterviewPhase.HR, "hr"));
        doAnswer(invocation -> {
            StreamingResponseHandler<AiMessage> handler = invocation.getArgument(1);
            handler.onNext("请讲一次团队冲突处理经历");
            handler.onComplete(Response.from(new AiMessage("请讲一次团队冲突处理经历")));
            return null;
        }).when(streamingChatModel).generate(anyList(), any());

        interviewService.chatStream(1L, 21L, "我上一题答完了");

        ArgumentCaptor<QuestionBankSearchRequest> captor = ArgumentCaptor.forClass(QuestionBankSearchRequest.class);
        verify(questionBankService).searchWithMetadata(captor.capture());
        QuestionBankSearchRequest request = captor.getValue();
        assertThat(request.getPosition()).isEqualTo("Java 后端开发");
        assertThat(request.getCategories()).containsExactly("HR软技能");
        assertThat(request.getExcludeAtomIds()).containsExactly("used-atom");
    }

    @Test
    @DisplayName("题库检索零命中时仍记录成功请求日志")
    void shouldLogSuccessfulRequestWhenSearchReturnsNoHits() {
        stubChatStream(22L, InterviewPhase.TECHNICAL);
        when(questionBankService.searchWithMetadata(any())).thenReturn(QuestionBankSearchResponse.builder()
                .results(List.of())
                .strategy("MYSQL_FALLBACK")
                .build());

        interviewService.chatStream(1L, 22L, "请继续");

        ArgumentCaptor<RagRetrievalRequestLog> captor = ArgumentCaptor.forClass(RagRetrievalRequestLog.class);
        verify(ragRetrievalRequestLogMapper).insert(captor.capture());
        RagRetrievalRequestLog requestLog = captor.getValue();
        assertThat(requestLog.getRequestId()).isNotBlank();
        assertThat(requestLog.getTurnIndex()).isEqualTo(1);
        assertThat(requestLog.getRequestedLimit()).isEqualTo(3);
        assertThat(requestLog.getCandidateCount()).isZero();
        assertThat(requestLog.getRetrievalStrategy()).isEqualTo("MYSQL_FALLBACK");
        assertThat(requestLog.getStatus()).isEqualTo("SUCCESS");
        verifyNoInteractions(ragRetrievalLogMapper);
    }

    @Test
    @DisplayName("题库检索命中日志关联同一请求 ID")
    void shouldLinkHitLogsToRetrievalRequest() {
        stubChatStream(23L, InterviewPhase.TECHNICAL);
        QuestionBankSearchResult result = QuestionBankSearchResult.builder()
                .atomId("atom-1")
                .category("JAVA")
                .score(0.91)
                .promptContext("HashMap 原理")
                .build();
        when(questionBankService.searchWithMetadata(any())).thenReturn(QuestionBankSearchResponse.builder()
                .results(List.of(result))
                .strategy("QDRANT_VECTOR")
                .build());

        interviewService.chatStream(1L, 23L, "请继续");

        ArgumentCaptor<RagRetrievalRequestLog> requestCaptor =
                ArgumentCaptor.forClass(RagRetrievalRequestLog.class);
        ArgumentCaptor<RagRetrievalLog> hitCaptor = ArgumentCaptor.forClass(RagRetrievalLog.class);
        verify(ragRetrievalRequestLogMapper).insert(requestCaptor.capture());
        verify(ragRetrievalLogMapper).insert(hitCaptor.capture());
        assertThat(requestCaptor.getValue().getRequestId()).isNotBlank();
        assertThat(hitCaptor.getValue().getRequestId())
                .isEqualTo(requestCaptor.getValue().getRequestId());
    }

    @Test
    @DisplayName("无需检索的阶段记录 SKIPPED 成功请求日志")
    void shouldLogSkippedRequestWhenSearchRequestIsNull() {
        stubChatStream(24L, InterviewPhase.OPENING);

        interviewService.chatStream(1L, 24L, "你好");

        ArgumentCaptor<RagRetrievalRequestLog> captor = ArgumentCaptor.forClass(RagRetrievalRequestLog.class);
        verify(ragRetrievalRequestLogMapper).insert(captor.capture());
        RagRetrievalRequestLog requestLog = captor.getValue();
        assertThat(requestLog.getTurnIndex()).isEqualTo(1);
        assertThat(requestLog.getRequestedLimit()).isZero();
        assertThat(requestLog.getCandidateCount()).isZero();
        assertThat(requestLog.getRetrievalStrategy()).isEqualTo("SKIPPED");
        assertThat(requestLog.getStatus()).isEqualTo("SUCCESS");
        verifyNoInteractions(questionBankService, ragRetrievalLogMapper);
    }

    private void stubChatStream(Long recordId, InterviewPhase nextPhase) {
        InterviewRecord record = new InterviewRecord();
        record.setId(recordId);
        record.setUserId(1L);
        record.setPosition("Java 后端开发");
        record.setPhase(InterviewPhase.OPENING.name());

        when(interviewRecordMapper.selectOne(any())).thenReturn(record);
        when(sessionStore.load(recordId)).thenReturn(new ArrayList<>());
        when(sessionStore.loadUsedAtoms(recordId)).thenReturn(List.of());
        when(sessionStore.loadTailoredQuestions(recordId)).thenReturn(List.of());
        when(interviewTurnPlanner.determineNextPhase(any(), anyList())).thenReturn(nextPhase);
        when(interviewTurnPlanner.plan(any(), anyList(), any(), any()))
                .thenReturn(new InterviewTurnPlanner.InterviewTurnPlan(nextPhase, "coordinator"));
        doAnswer(invocation -> {
            StreamingResponseHandler<AiMessage> handler = invocation.getArgument(1);
            handler.onNext("下一题");
            handler.onComplete(Response.from(new AiMessage("下一题")));
            return null;
        }).when(streamingChatModel).generate(anyList(), any());
    }
}
