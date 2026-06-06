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
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.StreamingResponseHandler;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import dev.langchain4j.model.output.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.core.task.TaskExecutor;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.never;
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
    private AppEventService appEventService;

    @Mock
    private InterviewTurnPlanner interviewTurnPlanner;

    @Mock
    private EvaluationGenerator evaluationGenerator;

    @Mock
    private MentorService mentorService;

    @Mock
    private TaskExecutor mentorTaskExecutor;

    @InjectMocks
    private InterviewServiceImpl interviewService;

    @BeforeEach
    void setUpRetrievalModule() {
        InterviewRetrievalService retrievalService = new InterviewRetrievalService(
                questionBankService, ragRetrievalLogMapper, ragRetrievalRequestLogMapper, appEventService);
        ReflectionTestUtils.setField(interviewService, "interviewRetrievalService", retrievalService);
    }

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
    @DisplayName("完成面试后通过受管执行器预热 Mentor 缓存")
    void shouldWarmMentorCacheWithManagedExecutor() {
        InterviewRecord record = new InterviewRecord();
        record.setId(12L);
        record.setUserId(1L);
        record.setPosition("AI 大模型工程师");
        record.setPhase(InterviewPhase.TECHNICAL.name());
        when(interviewRecordMapper.selectOne(any())).thenReturn(record);
        when(sessionStore.load(12L)).thenReturn(new ArrayList<>(List.of(
                new UserMessage("回答"),
                new AiMessage("问题"))));
        when(sessionStore.loadUsedAtoms(12L)).thenReturn(List.of("atom-1"));

        interviewService.endInterview(1L, 12L, 0, null);

        verify(mentorTaskExecutor).execute(any(Runnable.class));
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

        ArgumentCaptor<String> appEventErrorCaptor = ArgumentCaptor.forClass(String.class);
        verify(appEventService).recordSystemEvent(
                any(), any(), any(), any(), any(Boolean.class), appEventErrorCaptor.capture());
        assertThat(appEventErrorCaptor.getValue())
                .isEqualTo(requestLog.getErrorMessage())
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
        assertThat(request.getLimit()).isEqualTo(20);
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
        assertThat(requestLog.getRequestedLimit()).isEqualTo(20);
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
    @DisplayName("only marks prompt-context atoms as used after a successful stream")
    void shouldMarkOnlyContextAtomsUsedAfterSuccessfulStream() {
        stubChatStream(30L, InterviewPhase.TECHNICAL);
        List<QuestionBankSearchResult> candidates = new ArrayList<>();
        for (int i = 1; i <= 12; i++) {
            candidates.add(QuestionBankSearchResult.builder()
                    .atomId("atom-" + i)
                    .category("AI大模型")
                    .score(1.0 - i * 0.01)
                    .promptContext("context-" + i)
                    .build());
        }
        when(questionBankService.searchWithMetadata(any())).thenReturn(QuestionBankSearchResponse.builder()
                .results(candidates)
                .strategy("QDRANT_VECTOR")
                .build());

        interviewService.chatStream(1L, 30L, "请继续");

        ArgumentCaptor<List<String>> usedCaptor = ArgumentCaptor.forClass(List.class);
        verify(sessionStore).addUsedAtoms(org.mockito.ArgumentMatchers.eq(30L), usedCaptor.capture());
        assertThat(usedCaptor.getValue())
                .containsExactly("atom-1", "atom-2", "atom-3", "atom-4", "atom-5",
                        "atom-6", "atom-7", "atom-8", "atom-9", "atom-10");
        ArgumentCaptor<RagRetrievalLog> hitCaptor = ArgumentCaptor.forClass(RagRetrievalLog.class);
        verify(ragRetrievalLogMapper, org.mockito.Mockito.times(12)).insert(hitCaptor.capture());
        assertThat(hitCaptor.getAllValues().subList(0, 10))
                .allMatch(log -> Boolean.TRUE.equals(log.getContextSelected()));
        assertThat(hitCaptor.getAllValues().subList(10, 12))
                .allMatch(log -> Boolean.FALSE.equals(log.getContextSelected()));
    }

    @Test
    @DisplayName("does not consume context atoms when the AI stream fails")
    void shouldNotMarkAtomsUsedWhenStreamFails() {
        InterviewRecord record = stubChatStreamWithoutStreaming(31L, InterviewPhase.TECHNICAL);
        QuestionBankSearchResult result = QuestionBankSearchResult.builder()
                .atomId("atom-failed")
                .category("AI大模型")
                .score(0.9)
                .promptContext("failed context")
                .build();
        when(questionBankService.searchWithMetadata(any())).thenReturn(QuestionBankSearchResponse.builder()
                .results(List.of(result))
                .strategy("QDRANT_VECTOR")
                .build());
        doAnswer(invocation -> {
            StreamingResponseHandler<AiMessage> handler = invocation.getArgument(1);
            handler.onError(new IllegalStateException("stream failed"));
            return null;
        }).when(streamingChatModel).generate(anyList(), any());

        interviewService.chatStream(record.getUserId(), record.getId(), "请继续");

        verify(sessionStore, never()).addUsedAtoms(any(), anyList());
    }

    @Test
    @DisplayName("rejects a second active turn for the same interview record")
    void shouldRejectConcurrentTurnForSameRecord() {
        stubChatStreamWithoutStreaming(33L, InterviewPhase.TECHNICAL);
        when(questionBankService.searchWithMetadata(any())).thenReturn(QuestionBankSearchResponse.builder()
                .results(List.of())
                .strategy("MYSQL_FALLBACK")
                .build());
        AtomicBoolean nested = new AtomicBoolean();
        doAnswer(invocation -> {
            StreamingResponseHandler<AiMessage> handler = invocation.getArgument(1);
            if (nested.compareAndSet(false, true)) {
                interviewService.chatStream(1L, 33L, "重复提交");
            }
            handler.onNext("下一题");
            handler.onComplete(Response.from(new AiMessage("下一题")));
            return null;
        }).when(streamingChatModel).generate(anyList(), any());

        interviewService.chatStream(1L, 33L, "第一次提交");

        verify(streamingChatModel).generate(anyList(), any());
        verify(questionBankService).searchWithMetadata(any());
    }

    @Test
    @DisplayName("records degraded status when vector search falls back after infrastructure failure")
    void shouldLogDegradedRetrievalStatus() {
        stubChatStream(32L, InterviewPhase.TECHNICAL);
        when(questionBankService.searchWithMetadata(any())).thenReturn(QuestionBankSearchResponse.builder()
                .results(List.of())
                .strategy("MYSQL_FALLBACK_DEGRADED")
                .build());

        interviewService.chatStream(1L, 32L, "请继续");

        ArgumentCaptor<RagRetrievalRequestLog> captor =
                ArgumentCaptor.forClass(RagRetrievalRequestLog.class);
        verify(ragRetrievalRequestLogMapper).insert(captor.capture());
        assertThat(captor.getValue().getRetrievalStrategy()).isEqualTo("MYSQL_FALLBACK_DEGRADED");
        assertThat(captor.getValue().getStatus()).isEqualTo("DEGRADED");
        assertThat(captor.getValue().getErrorMessage()).contains("Qdrant");
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

    @Test
    @DisplayName("请求日志写入失败时仍继续请求 AI 输出")
    void shouldContinueStreamingWhenRetrievalRequestLogInsertFails() {
        stubChatStream(25L, InterviewPhase.TECHNICAL);
        when(questionBankService.searchWithMetadata(any())).thenReturn(QuestionBankSearchResponse.builder()
                .results(List.of())
                .strategy("MYSQL_FALLBACK")
                .build());
        when(ragRetrievalRequestLogMapper.insert(any()))
                .thenThrow(new IllegalStateException("api_key=raw-key"));

        SseEmitter emitter = interviewService.chatStream(1L, 25L, "请继续");

        assertThat(emitter).isNotNull();
        verify(streamingChatModel).generate(anyList(), any());
    }

    @Test
    @DisplayName("每轮 AI 回复完成后增量持久化对话历史")
    void shouldPersistChatHistoryAfterEachCompletedTurn() {
        stubChatStream(26L, InterviewPhase.OPENING);

        interviewService.chatStream(1L, 26L, "请开始");

        ArgumentCaptor<InterviewRecord> captor = ArgumentCaptor.forClass(InterviewRecord.class);
        verify(interviewRecordMapper, atLeast(2)).updateById(captor.capture());
        assertThat(captor.getAllValues())
                .anySatisfy(update -> assertThat(update.getChatHistory())
                        .contains("\"type\":\"USER\"", "\"text\":\"请开始\"",
                                "\"type\":\"AI\"", "\"text\":\"下一题\""));
    }

    @Test
    @DisplayName("Redis 会话丢失时从数据库对话历史恢复并继续面试")
    void shouldRestoreChatHistoryFromDatabaseWhenSessionMissing() {
        InterviewRecord record = new InterviewRecord();
        record.setId(27L);
        record.setUserId(1L);
        record.setPosition("AI 大模型工程师");
        record.setPhase(InterviewPhase.TECHNICAL.name());
        record.setChatHistory("""
                [
                  {"type":"USER","text":"你好"},
                  {"type":"AI","text":"请解释 RAG 的基本流程"}
                ]
                """);

        when(interviewRecordMapper.selectOne(any())).thenReturn(record);
        when(sessionStore.load(27L)).thenReturn(null);
        when(sessionStore.loadUsedAtoms(27L)).thenReturn(List.of());
        when(sessionStore.loadTailoredQuestions(27L)).thenReturn(List.of());
        when(interviewTurnPlanner.determineNextPhase(any(), anyList()))
                .thenReturn(InterviewPhase.TECHNICAL);
        when(questionBankService.searchWithMetadata(any())).thenReturn(QuestionBankSearchResponse.builder()
                .results(List.of())
                .strategy("MYSQL_FALLBACK")
                .build());
        when(interviewTurnPlanner.plan(any(), anyList(), any(), any()))
                .thenReturn(new InterviewTurnPlanner.InterviewTurnPlan(InterviewPhase.TECHNICAL, "coordinator"));
        doAnswer(invocation -> {
            StreamingResponseHandler<AiMessage> handler = invocation.getArgument(1);
            handler.onNext("继续追问");
            handler.onComplete(Response.from(new AiMessage("继续追问")));
            return null;
        }).when(streamingChatModel).generate(anyList(), any());

        interviewService.chatStream(1L, 27L, "向量数据库负责相似度检索");

        ArgumentCaptor<List<dev.langchain4j.data.message.ChatMessage>> historyCaptor =
                ArgumentCaptor.forClass(List.class);
        verify(sessionStore, atLeast(1)).save(any(), historyCaptor.capture());
        assertThat(historyCaptor.getAllValues())
                .anySatisfy(history -> assertThat(history)
                        .hasSize(4)
                        .anySatisfy(message -> assertThat(message).isInstanceOf(UserMessage.class))
                        .anySatisfy(message -> assertThat(message).isInstanceOf(AiMessage.class)));
        verify(streamingChatModel).generate(anyList(), any());
    }

    private void stubChatStream(Long recordId, InterviewPhase nextPhase) {
        stubChatStreamWithoutStreaming(recordId, nextPhase);
        doAnswer(invocation -> {
            StreamingResponseHandler<AiMessage> handler = invocation.getArgument(1);
            handler.onNext("下一题");
            handler.onComplete(Response.from(new AiMessage("下一题")));
            return null;
        }).when(streamingChatModel).generate(anyList(), any());
    }

    private InterviewRecord stubChatStreamWithoutStreaming(Long recordId, InterviewPhase nextPhase) {
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
        return record;
    }
}
