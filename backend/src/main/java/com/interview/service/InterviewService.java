package com.interview.service;

import com.interview.entity.InterviewRecord;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

public interface InterviewService {
    
    // Start an interview, initialize AI, save to db, return recordId
    Long startInterview(Long userId, String position);

    // Start an interview with mode (text/video)
    Long startInterview(Long userId, String position, String mode);
    
    // Send a message and get SSE stream for typing animation
    SseEmitter chatStream(Long userId, Long recordId, String message);
    
    // End interview and generate evaluation report
    InterviewRecord endInterview(Long recordId);

    // End interview with voice behavioral metrics (wpm = words per minute)
    InterviewRecord endInterview(Long recordId, Integer wpm);

    // End interview with voice metrics and emotion analysis data
    InterviewRecord endInterview(Long recordId, Integer wpm, String emotionJson);
}
