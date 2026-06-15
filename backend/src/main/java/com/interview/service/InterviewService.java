package com.interview.service;

import com.interview.dto.FinishInterviewResponse;
import com.interview.entity.InterviewRecord;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

public interface InterviewService {

    Long startInterview(Long userId, String position);

    Long startInterview(Long userId, String position, String mode);

    Long startInterview(Long userId, String position, String mode, java.util.List<String> resumeQuestions);

    Long startInterview(Long userId, String position, String mode, java.util.List<String> resumeQuestions,
                        String difficultyLevel, java.util.List<String> focusAreas);

    Long startInterview(Long userId, String position, String mode, java.util.List<String> resumeQuestions,
                        String difficultyLevel, java.util.List<String> focusAreas, Long positionId);

    SseEmitter chatStream(Long userId, Long recordId, String message);

    InterviewRecord endInterview(Long recordId);

    InterviewRecord endInterview(Long recordId, Integer wpm);

    InterviewRecord endInterview(Long recordId, Integer wpm, String emotionJson);

    InterviewRecord endInterview(Long userId, Long recordId, Integer wpm, String emotionJson);

    FinishInterviewResponse finishInterview(Long userId, Long recordId, Integer wpm, String emotionJson);

    /** 丢弃当前用户未完成的面试记录，不生成报告 */
    void discardInterview(Long userId, Long recordId);

    /** 查询用户面试历史列表（已评分，按时间倒序，最多50条） */
    List<InterviewRecord> getHistoryList(Long userId);

    /** 查询当前用户单场面试详情 */
    InterviewRecord getHistoryDetail(Long userId, Long recordId);
}
