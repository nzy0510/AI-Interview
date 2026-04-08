package com.interview.controller;

import com.interview.common.Result;
import com.interview.service.InterviewService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import java.util.Map;
import jakarta.servlet.http.HttpServletRequest;

/**
 * 面试控制器：提供面试相关的三个核心 API 接口
 * - POST /start 开始一场新面试
 * - GET /chatStream SSE 流式对话（打字机效果）
 * - POST /finish 结束面试并生成 AI 评价报告
 */
@RestController
@RequestMapping("/api/interview")
@CrossOrigin
public class InterviewController {

    @Autowired
    private InterviewService interviewService;

    /**
     * 开始新面试：创建数据库记录，初始化 AI 聊天记忆，返回面试记录 ID
     */
    @PostMapping("/start")
    public Result<Long> startInterview(@RequestBody Map<String, Object> params, HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("currentUserId");
        String position = (String) params.get("position");
        String mode = params.get("mode") != null ? params.get("mode").toString() : "text";
        java.util.List<String> resumeQuestions = (java.util.List<String>) params.get("resumeQuestions");
        Long recordId = interviewService.startInterview(userId, position, mode, resumeQuestions);
        return Result.success(recordId);
    }

    /**
     * SSE 流式对话接口
     */
    @GetMapping(value = "/chatStream", produces = "text/event-stream;charset=UTF-8")
    public SseEmitter chatStream(
            @RequestParam("recordId") Long recordId,
            @RequestParam("message") String message,
            HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("currentUserId");
        return interviewService.chatStream(userId, recordId, message);
    }

    /**
     * 结束面试：清理 AI 会话记忆，调用大模型生成综合评价报告
     */
    @PostMapping("/finish")
    public Result<?> finishInterview(@RequestBody Map<String, Object> params) {
        Long recordId = Long.valueOf(params.get("recordId").toString());
        Integer wpm = params.get("wpm") != null ? Integer.valueOf(params.get("wpm").toString()) : 0;
        String emotionJson = params.get("emotionJson") != null ? params.get("emotionJson").toString() : null;
        com.interview.entity.InterviewRecord record = interviewService.endInterview(recordId, wpm, emotionJson);
        return Result.success(record);
    }
}
