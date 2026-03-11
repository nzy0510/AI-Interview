package com.interview.controller;

import com.interview.common.Result;
import com.interview.service.InterviewService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;

@RestController
@RequestMapping("/api/interview")
@CrossOrigin // Allows cross-origin requests from Vue
public class InterviewController {

    @Autowired
    private InterviewService interviewService;

    /**
     * Start a new interview session
     */
    @PostMapping("/start")
    public Result<Long> startInterview(@RequestBody Map<String, Object> params) {
        // In a real app, userId should come from JWT Token interceptor.
        // For simplicity now, we can pass it or hardcode.
        // Let's assume frontend passes a minimal payload.
        Long userId = 1L; // Hardcoded for demo, normally we extract from token Context
        String position = (String) params.get("position");
        Long recordId = interviewService.startInterview(userId, position);
        return Result.success(recordId);
    }

    /**
     * SSE endpoint for chatting with AI
     */
    @GetMapping(value = "/chatStream", produces = "text/event-stream;charset=UTF-8")
    public SseEmitter chatStream(
            @RequestParam("recordId") Long recordId,
            @RequestParam("message") String message) {
        
        Long userId = 1L; // Mock userId
        return interviewService.chatStream(userId, recordId, message);
    }

    /**
     * Finish interview and generate report
     */
    @PostMapping("/finish")
    public Result<?> finishInterview(@RequestBody Map<String, Object> params) {
        Long recordId = Long.valueOf(params.get("recordId").toString());
        com.interview.entity.InterviewRecord record = interviewService.endInterview(recordId);
        return Result.success(record);
    }
}
