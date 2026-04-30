package com.interview.service;

import com.interview.entity.InterviewRecord;
import com.interview.mapper.InterviewRecordMapper;
import com.interview.service.impl.InterviewServiceImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("InterviewService — 面试记录归属校验")
@ExtendWith(MockitoExtension.class)
class InterviewServiceImplTest {

    @Mock
    private InterviewRecordMapper interviewRecordMapper;

    @Mock
    private SessionStore sessionStore;

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
}
