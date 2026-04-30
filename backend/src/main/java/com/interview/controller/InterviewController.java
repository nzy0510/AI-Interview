package com.interview.controller;

import com.interview.common.Result;
import com.interview.dto.FinishInterviewRequest;
import com.interview.dto.StartInterviewRequest;
import com.interview.service.InterviewService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import jakarta.servlet.http.HttpServletRequest;

/**
 * 面试控制器：提供面试相关的三个核心 API 接口
 */
@RestController
@RequestMapping("/api/interview")
@CrossOrigin
public class InterviewController {

    @Autowired
    private InterviewService interviewService;

    @PostMapping("/start")
    public Result<Long> startInterview(@Valid @RequestBody StartInterviewRequest req,
                                      HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("currentUserId");
        Long recordId = interviewService.startInterview(userId, req.getPosition(),
                req.getMode(), req.getResumeQuestions());
        return Result.success(recordId);
    }

    @GetMapping(value = "/chatStream", produces = "text/event-stream;charset=UTF-8")
    public SseEmitter chatStream(
            @RequestParam("recordId") Long recordId,
            @RequestParam("message") String message,
            HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("currentUserId");
        return interviewService.chatStream(userId, recordId, message);
    }

    @PostMapping("/finish")
    public Result<?> finishInterview(@Valid @RequestBody FinishInterviewRequest req,
                                     HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("currentUserId");
        int wpm = req.getWpm() != null ? req.getWpm() : 0;
        com.interview.entity.InterviewRecord record = interviewService.endInterview(
                userId, req.getRecordId(), wpm, req.getEmotionJson());
        return Result.success(record);
    }
}
