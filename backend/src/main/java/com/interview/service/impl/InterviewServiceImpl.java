package com.interview.service.impl;

import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.interview.entity.InterviewPhase;
import com.interview.entity.InterviewRecord;
import com.interview.exception.LlmProviderRequiredException;
import com.interview.mapper.InterviewRecordMapper;
import com.interview.service.InterviewRetrievalService;
import com.interview.service.InterviewService;
import com.interview.service.InterviewTurnPlanner;
import com.interview.service.UserLlmConfigService;
import com.interview.service.UserLlmModelFactory;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;

import dev.langchain4j.model.StreamingResponseHandler;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import dev.langchain4j.model.output.Response;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

@Service
@Slf4j
public class InterviewServiceImpl implements InterviewService {

    private static final Pattern SENSITIVE_KEY_VALUE_PATTERN = Pattern.compile(
            "(?i)\\b(api[_-]?key|token|password|secret)\\s*([=:])\\s*([^\\s,;]+)");
    private static final Pattern URL_PATTERN = Pattern.compile("https?://\\S+", Pattern.CASE_INSENSITIVE);
    private final Set<Long> activeInterviewTurns = ConcurrentHashMap.newKeySet();

    @Autowired
    private InterviewRecordMapper interviewRecordMapper;

    @Autowired
    private UserLlmConfigService userLlmConfigService;

    @Autowired
    private UserLlmModelFactory userLlmModelFactory;

    @Autowired
    private com.interview.service.SessionStore sessionStore;

    @Autowired
    private com.interview.service.EvaluationGenerator evaluationGenerator;

    @Autowired
    private InterviewRetrievalService interviewRetrievalService;

    @Autowired
    private com.interview.service.MentorService mentorService;

    @Autowired
    @Qualifier("mentorTaskExecutor")
    private TaskExecutor mentorTaskExecutor;

    @Autowired
    private InterviewTurnPlanner interviewTurnPlanner;

    @Autowired(required = false)
    private com.interview.service.AppEventService appEventService;

    // ========== 业务方法 ==========

    @Override
    public Long startInterview(Long userId, String position) {
        return startInterview(userId, position, "text");
    }

    @Override
    public Long startInterview(Long userId, String position, String mode) {
        return startInterview(userId, position, mode, null);
    }

    @Override
    public Long startInterview(Long userId, String position, String mode, List<String> resumeQuestions) {
        return startInterview(userId, position, mode, resumeQuestions, "mid", null);
    }

    @Override
    public Long startInterview(Long userId, String position, String mode, List<String> resumeQuestions,
                               String difficultyLevel, List<String> focusAreas) {
        userLlmConfigService.ensureActiveProvider(userId);

        InterviewRecord record = new InterviewRecord();
        record.setUserId(userId);
        record.setPosition(position);
        record.setPhase(InterviewPhase.OPENING.name());
        record.setInterviewMode(normalizeMode(mode));
        record.setDifficultyLevel(normalizeDifficulty(difficultyLevel));
        record.setFocusAreas(serializeFocusAreas(focusAreas));
        record.setCreateTime(LocalDateTime.now());
        record.setChatHistory("[]");
        interviewRecordMapper.insert(record);

        sessionStore.save(record.getId(), new ArrayList<>());

        if (resumeQuestions != null && !resumeQuestions.isEmpty()) {
            sessionStore.saveTailoredQuestions(record.getId(), resumeQuestions);
        }

        return record.getId();
    }

    @Override
    public SseEmitter chatStream(Long userId, Long recordId, String message) {
        SseEmitter emitter = new SseEmitter(0L);

        InterviewRecord record;
        try {
            record = loadOwnedRecord(userId, recordId);
        } catch (RuntimeException e) {
            sendSseError(emitter, e.getMessage());
            return emitter;
        }

        List<ChatMessage> chatHistory = sessionStore.load(recordId);

        if (chatHistory == null) {
            chatHistory = parsePersistedChatHistory(record.getChatHistory());
            if (chatHistory.isEmpty()) {
                try {
                    emitter.send(JSON.toJSONString(Map.of("error", "session_expired")));
                    emitter.complete();
                } catch (IOException e) {
                }
                return emitter;
            }
            sessionStore.save(recordId, chatHistory);
        }

        if (!activeInterviewTurns.add(recordId)) {
            sendSseError(emitter, "当前轮次正在处理中，请勿重复提交");
            return emitter;
        }

        try {
            OpenAiStreamingChatModel streamingChatModel =
                    userLlmModelFactory.createStreamingChatModel(userLlmConfigService.requireActiveRuntimeConfig(userId));
            InterviewPhase nextPhase = interviewTurnPlanner.determineNextPhase(record, chatHistory);
            List<String> usedAtomIds = sessionStore.loadUsedAtoms(recordId);
            InterviewRetrievalService.TurnRetrieval retrieval = interviewRetrievalService.retrieve(
                    userId, record, chatHistory, message, nextPhase, usedAtomIds);
            List<String> contextAtomIds = retrieval.contextAtomIds();

            // 2. 根据显式面试阶段选择 Agent 人设（替代隐式 turn 计算）
            List<String> tailoredQuestions = sessionStore.loadTailoredQuestions(recordId);
            InterviewTurnPlanner.InterviewTurnPlan turnPlan =
                    interviewTurnPlanner.plan(record, chatHistory, retrieval.promptContext(), tailoredQuestions);
            record.setPhase(turnPlan.phase().name());
            interviewRecordMapper.updateById(record);
            try {
                emitter.send(JSON.toJSONString(Map.of("phase", turnPlan.phase().name())));
            } catch (IOException e) {
                activeInterviewTurns.remove(recordId);
                emitter.completeWithError(e);
                return emitter;
            }

            // 3. 构造消息列表
            List<ChatMessage> messages = new ArrayList<>();
            messages.add(new SystemMessage(turnPlan.systemPrompt()));
            messages.addAll(chatHistory);
            messages.add(new UserMessage(message));

            // 4. SSE 流式输出
            final List<ChatMessage> currentHistory = chatHistory;
            StringBuilder aiResponseBuilder = new StringBuilder();
            streamingChatModel.generate(messages, new StreamingResponseHandler<AiMessage>() {
                @Override
                public void onNext(String token) {
                    try {
                        aiResponseBuilder.append(token);
                        emitter.send(JSON.toJSONString(Map.of("content", token)));
                    } catch (IOException e) {
                        emitter.completeWithError(e);
                    }
                }

                @Override
                public void onComplete(Response<AiMessage> response) {
                    try {
                        currentHistory.add(new UserMessage(message));
                        currentHistory.add(new AiMessage(aiResponseBuilder.toString()));
                        sessionStore.save(recordId, currentHistory);
                        persistChatHistory(recordId, currentHistory);
                        sessionStore.addUsedAtoms(recordId, contextAtomIds);

                        emitter.send(JSON.toJSONString(Map.of("done", "true")));
                        emitter.complete();
                    } catch (Exception e) {
                        emitter.completeWithError(e);
                    } finally {
                        activeInterviewTurns.remove(recordId);
                    }
                }

                @Override
                public void onError(Throwable error) {
                    log.error("AI 响应错误: {}", sanitizeErrorMessage(error.getMessage()));
                    recordSystemEvent(userId, "DEEPSEEK_STREAM_FAILED", "system",
                            Map.of("recordId", recordId), false, sanitizeErrorMessage(error.getMessage()));
                    try {
                        emitter.send(JSON.toJSONString(Map.of("error", "AI 响应异常，请稍后重试")));
                        emitter.complete();
                    } catch (IOException e) {
                    } finally {
                        activeInterviewTurns.remove(recordId);
                    }
                }
            });
        } catch (LlmProviderRequiredException e) {
            activeInterviewTurns.remove(recordId);
            sendSseError(emitter, e.getMessage());
        } catch (RuntimeException e) {
            activeInterviewTurns.remove(recordId);
            log.warn("面试轮次启动失败: recordId={}, error={}", recordId, sanitizeErrorMessage(e.getMessage()));
            sendSseError(emitter, "面试轮次启动失败，请稍后重试");
        }

        return emitter;
    }

    private String sanitizeErrorMessage(String errorMessage) {
        if (errorMessage == null) {
            return null;
        }
        String sanitized = URL_PATTERN.matcher(errorMessage).replaceAll("[URL]");
        sanitized = SENSITIVE_KEY_VALUE_PATTERN.matcher(sanitized).replaceAll("$1$2[REDACTED]");
        return truncate(sanitized, 500);
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    @Override
    public InterviewRecord endInterview(Long recordId) {
        return endInterview(recordId, 0, null);
    }

    @Override
    public InterviewRecord endInterview(Long recordId, Integer wpm) {
        return endInterview(recordId, wpm, null);
    }

    @Override
    public InterviewRecord endInterview(Long recordId, Integer wpm, String emotionJson) {
        InterviewRecord record = interviewRecordMapper.selectById(recordId);
        if (record == null) {
            log.error("面试记录不存在: recordId={}", recordId);
            return null;
        }
        return completeInterview(record, wpm, emotionJson);
    }

    @Override
    public InterviewRecord endInterview(Long userId, Long recordId, Integer wpm, String emotionJson) {
        InterviewRecord record = loadOwnedRecord(userId, recordId);
        return completeInterview(record, wpm, emotionJson);
    }

    @Override
    public void discardInterview(Long userId, Long recordId) {
        InterviewRecord record = loadOwnedRecord(userId, recordId);
        if (InterviewPhase.FINISHED.name().equals(record.getPhase())
                || record.getEndTime() != null
                || record.getScore() != null) {
            throw new RuntimeException("已完成的面试记录不能退出");
        }
        if (activeInterviewTurns.contains(recordId)) {
            throw new RuntimeException("当前轮次正在处理中，请稍后退出");
        }

        int deleted = interviewRecordMapper.delete(new LambdaQueryWrapper<InterviewRecord>()
                .eq(InterviewRecord::getId, recordId)
                .eq(InterviewRecord::getUserId, userId));
        if (deleted != 1) {
            throw new RuntimeException("面试记录不存在或无权访问");
        }
        sessionStore.delete(recordId);
        activeInterviewTurns.remove(recordId);
    }

    private InterviewRecord completeInterview(InterviewRecord record, Integer wpm, String emotionJson) {
        Long recordId = record.getId();
        if (InterviewPhase.FINISHED.name().equals(record.getPhase()) && record.getScore() != null) {
            log.info("面试已完成，跳过重复评估生成 recordId={}", recordId);
            return record;
        }

        List<ChatMessage> historyMessages = sessionStore.load(recordId);
        // 持久化已用知识原子 ID 列表
        List<String> usedAtomIds = sessionStore.loadUsedAtoms(recordId);
        sessionStore.delete(recordId);

        record.setEndTime(LocalDateTime.now());
        record.setPhase(InterviewPhase.FINISHED.name());
        record.setVoiceWpm(wpm != null ? wpm : 0);
        record.setUsedAtomIds(usedAtomIds != null && !usedAtomIds.isEmpty()
                ? JSON.toJSONString(usedAtomIds) : null);
        if (emotionJson != null && !emotionJson.isEmpty()) {
            record.setEmotionJson(emotionJson);
        }

        if (historyMessages == null) {
            historyMessages = new ArrayList<>();
        }

        if (!historyMessages.isEmpty()) {
            record.setChatHistory(serializeChatHistory(historyMessages));
        } else {
            log.warn("缓存中无会话 (recordId={})，尝试从数据库恢复", recordId);
            historyMessages = parsePersistedChatHistory(record.getChatHistory());
            if (!historyMessages.isEmpty()) {
                log.info("从数据库恢复了 {} 条对话消息", historyMessages.size());
            }
        }

        if (historyMessages.isEmpty()) {
            log.warn("对话历史为空，跳过 AI 评估 (recordId={})", recordId);
            record.setScore(0);
            record.setFeedback("面试对话为空，无法生成评估报告。请确保面试过程中有完整的对话记录。");
            interviewRecordMapper.updateById(record);
            return record;
        }

        // ========== AI 评估 ==========
        evaluationGenerator.generate(record, historyMessages, wpm);

        interviewRecordMapper.updateById(record);

        // 后台预计算 AI Mentor 缓存，避免 Dashboard 首次访问触发 LLM 阻塞
        final Long uid = record.getUserId();
        try {
            mentorTaskExecutor.execute(() -> {
                try {
                    mentorService.getInsight(uid);
                    log.info("AI Mentor 缓存已更新 userId={}", uid);
                } catch (Exception e) {
                    log.warn("AI Mentor 后台缓存更新失败 userId={}: {}", uid, e.getMessage());
                }
            });
        } catch (RuntimeException e) {
            log.warn("AI Mentor 缓存任务提交失败 userId={}: {}", uid, e.getMessage());
        }

        return record;
    }

    @Override
    public java.util.List<InterviewRecord> getHistoryList(Long userId) {
        com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<InterviewRecord> query =
                new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<>();
        query.eq("user_id", userId)
             .isNotNull("score")
             .orderByDesc("create_time")
             .last("LIMIT 50");
        return interviewRecordMapper.selectList(query);
    }

    @Override
    public InterviewRecord getHistoryDetail(Long userId, Long recordId) {
        return loadOwnedRecord(userId, recordId);
    }

    private InterviewRecord loadOwnedRecord(Long userId, Long recordId) {
        if (userId == null) {
            throw new RuntimeException("未登录：缺少用户身份");
        }
        InterviewRecord record = interviewRecordMapper.selectOne(
                new LambdaQueryWrapper<InterviewRecord>()
                        .eq(InterviewRecord::getId, recordId)
                        .eq(InterviewRecord::getUserId, userId));
        if (record == null) {
            throw new RuntimeException("面试记录不存在或无权访问");
        }
        return record;
    }

    private void persistChatHistory(Long recordId, List<ChatMessage> messages) {
        InterviewRecord update = new InterviewRecord();
        update.setId(recordId);
        update.setChatHistory(serializeChatHistory(messages));
        try {
            interviewRecordMapper.updateById(update);
        } catch (Exception e) {
            log.warn("对话历史增量持久化失败: recordId={}, error={}", recordId, sanitizeErrorMessage(e.getMessage()));
        }
    }

    private String serializeChatHistory(List<ChatMessage> messages) {
        List<Map<String, String>> serialized = new ArrayList<>();
        for (ChatMessage msg : messages) {
            Map<String, String> row = new HashMap<>();
            if (msg instanceof UserMessage userMessage) {
                row.put("type", "USER");
                row.put("text", userMessage.singleText());
            } else if (msg instanceof AiMessage aiMessage) {
                row.put("type", "AI");
                row.put("text", aiMessage.text());
            } else if (msg instanceof SystemMessage systemMessage) {
                row.put("type", "SYSTEM");
                row.put("text", systemMessage.text());
            } else {
                continue;
            }
            serialized.add(row);
        }
        return JSON.toJSONString(serialized);
    }

    private List<ChatMessage> parsePersistedChatHistory(String savedHistory) {
        List<ChatMessage> messages = new ArrayList<>();
        if (savedHistory == null || savedHistory.isBlank() || "[]".equals(savedHistory)) {
            return messages;
        }
        try {
            com.alibaba.fastjson2.JSONArray arr = JSON.parseArray(savedHistory);
            for (int i = 0; i < arr.size(); i++) {
                com.alibaba.fastjson2.JSONObject msgObj = arr.getJSONObject(i);
                String type = msgObj.getString("type");
                String text = msgObj.getString("text");
                if (text == null) {
                    com.alibaba.fastjson2.JSONArray contents = msgObj.getJSONArray("contents");
                    if (contents != null && !contents.isEmpty()) {
                        text = contents.getJSONObject(0).getString("text");
                    }
                }
                if (text == null) {
                    continue;
                }
                if ("AI".equals(type)) {
                    messages.add(new AiMessage(text));
                } else if ("USER".equals(type)) {
                    messages.add(new UserMessage(text));
                } else if ("SYSTEM".equals(type)) {
                    messages.add(new SystemMessage(text));
                }
            }
        } catch (Exception e) {
            log.error("解析数据库对话历史失败", e);
        }
        return messages;
    }

    private String normalizeMode(String mode) {
        return "video".equals(mode) ? "video" : "text";
    }

    private String normalizeDifficulty(String difficultyLevel) {
        if ("junior".equals(difficultyLevel)
                || "mid".equals(difficultyLevel)
                || "senior".equals(difficultyLevel)
                || "principal".equals(difficultyLevel)) {
            return difficultyLevel;
        }
        return "mid";
    }

    private String serializeFocusAreas(List<String> focusAreas) {
        if (focusAreas == null || focusAreas.isEmpty()) {
            return null;
        }
        List<String> cleaned = new ArrayList<>();
        for (String area : focusAreas) {
            if (area != null && !area.isBlank() && !cleaned.contains(area.trim())) {
                cleaned.add(area.trim());
            }
        }
        return cleaned.isEmpty() ? null : JSON.toJSONString(cleaned);
    }

    private void sendSseError(SseEmitter emitter, String message) {
        try {
            emitter.send(JSON.toJSONString(Map.of("error", message)));
            emitter.complete();
        } catch (IOException ignored) {
        }
    }

    private void recordSystemEvent(Long userId, String eventType, String category,
                                   Map<String, Object> metadata, boolean success, String errorMessage) {
        if (appEventService != null) {
            appEventService.recordSystemEvent(userId, eventType, category, metadata, success, errorMessage);
        }
    }
}
