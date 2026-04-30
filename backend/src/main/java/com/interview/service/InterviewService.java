package com.interview.service;

import com.interview.entity.InterviewRecord;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

public interface InterviewService {

    Long startInterview(Long userId, String position);

    Long startInterview(Long userId, String position, String mode);

    Long startInterview(Long userId, String position, String mode, java.util.List<String> resumeQuestions);

    Long startInterview(Long userId, String position, String mode, java.util.List<String> resumeQuestions,
                        String difficultyLevel, java.util.List<String> focusAreas);

    SseEmitter chatStream(Long userId, Long recordId, String message);

    InterviewRecord endInterview(Long recordId);

    InterviewRecord endInterview(Long recordId, Integer wpm);

    InterviewRecord endInterview(Long recordId, Integer wpm, String emotionJson);

    InterviewRecord endInterview(Long userId, Long recordId, Integer wpm, String emotionJson);

    /** 查询用户面试历史列表（已评分，按时间倒序，最多50条） */
    List<InterviewRecord> getHistoryList(Long userId);

    /** 查询当前用户单场面试详情 */
    InterviewRecord getHistoryDetail(Long userId, Long recordId);
}
