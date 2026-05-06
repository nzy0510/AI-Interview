package com.interview.service;

import com.interview.config.InterviewPrompts;
import com.interview.entity.InterviewPhase;
import com.interview.entity.InterviewRecord;
import com.interview.mapper.InterviewRecordMapper;
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
    private InterviewPrompts interviewPrompts;

    @Mock
    private QuestionBankService questionBankService;

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
        when(questionBankService.search(any()))
                .thenThrow(new IllegalArgumentException("未配置岗位对应的知识库分类: 测试开发"));
        when(interviewPrompts.getCoordinator()).thenReturn("coordinator");
        when(interviewPrompts.getAttitudeRule()).thenReturn("");
        doAnswer(invocation -> {
            StreamingResponseHandler<AiMessage> handler = invocation.getArgument(1);
            handler.onNext("你好");
            handler.onComplete(Response.from(new AiMessage("你好")));
            return null;
        }).when(streamingChatModel).generate(anyList(), any());

        SseEmitter emitter = interviewService.chatStream(1L, 20L, "你好");

        assertThat(emitter).isNotNull();
        verify(questionBankService).search(any());
        verify(streamingChatModel).generate(anyList(), any());
    }
}
