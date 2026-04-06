package com.interview.controller;

import com.interview.common.Result;
import com.interview.service.InterviewService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;

/**
 * 面试控制器：提供面试相关的三个核心 API 接口
 * - POST /start 开始一场新面试
 * - GET /chatStream SSE 流式对话（打字机效果）
 * - POST /finish 结束面试并生成 AI 评价报告
 */
@RestController
@RequestMapping("/api/interview")
@CrossOrigin // 允许跨域请求，便于 Vue 前端 (localhost:5174) 访问后端 (localhost:8080)
public class InterviewController {

    @Autowired
    private InterviewService interviewService;

    /**
     * 开始新面试：创建数据库记录，初始化 AI 聊天记忆，返回面试记录 ID
     * 前端会将返回的 recordId 用于后续的流式对话和结束面试接口
     */
    @PostMapping("/start")
    public Result<Long> startInterview(@RequestBody Map<String, Object> params) {
        // TODO: 实际项目中 userId 应从 JWT Token 拦截器中提取，这里简化为硬编码
        Long userId = 1L;
        String position = (String) params.get("position"); // 岗位名称，如 "Java后端开发"
        String mode = params.get("mode") != null ? params.get("mode").toString() : "text";
        java.util.List<String> resumeQuestions = (java.util.List<String>) params.get("resumeQuestions");
        Long recordId = interviewService.startInterview(userId, position, mode, resumeQuestions);
        return Result.success(recordId);
    }

    /**
     * SSE 流式对话接口：用户发送消息 → 后端 RAG 检索知识库 → AI 逐字流式回复
     * 前端通过浏览器原生 EventSource API 接收此接口的数据流
     * produces = "text/event-stream" 声明返回类型为 SSE 事件流
     */
    @GetMapping(value = "/chatStream", produces = "text/event-stream;charset=UTF-8")
    public SseEmitter chatStream(
            @RequestParam("recordId") Long recordId,
            @RequestParam("message") String message) {
        Long userId = 1L; // TODO: 从 JWT 中提取
        return interviewService.chatStream(userId, recordId, message);
    }

    /**
     * 结束面试：清理 AI 会话记忆，调用大模型生成综合评价报告
     * 现支持接收前端传回的语音表现数据 (wpm, voiceRounds)
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
