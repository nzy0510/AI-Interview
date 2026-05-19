package com.interview.service.impl;

import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.interview.entity.InterviewPhase;
import com.interview.entity.InterviewRecord;
import com.interview.entity.RagRetrievalLog;
import com.interview.dto.questionbank.QuestionBankSearchRequest;
import com.interview.dto.questionbank.QuestionBankSearchResult;
import com.interview.mapper.InterviewRecordMapper;
import com.interview.mapper.RagRetrievalLogMapper;
import com.interview.service.InterviewService;
import com.interview.service.InterviewTurnPlanner;
import com.interview.service.UsageQuotaService;
import com.interview.service.questionbank.QuestionBankService;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;

import dev.langchain4j.model.StreamingResponseHandler;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import dev.langchain4j.model.output.Response;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
public class InterviewServiceImpl implements InterviewService {

    @Autowired
    private InterviewRecordMapper interviewRecordMapper;

    @Autowired
    private OpenAiStreamingChatModel streamingChatModel;

    @Autowired
    private com.interview.service.SessionStore sessionStore;

    @Autowired
    private QuestionBankService questionBankService;

    @Autowired
    private com.interview.service.EvaluationGenerator evaluationGenerator;

    @Autowired
    private RagRetrievalLogMapper ragRetrievalLogMapper;

    @Autowired
    private com.interview.service.MentorService mentorService;

    @Autowired
    private InterviewTurnPlanner interviewTurnPlanner;

    @Autowired(required = false)
    private UsageQuotaService usageQuotaService;

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
        consumeQuota(userId, UsageQuotaService.INTERVIEW_START);

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

        try {
            consumeQuota(userId, UsageQuotaService.AI_CHAT_TURN);
        } catch (RuntimeException e) {
            sendSseError(emitter, e.getMessage());
            return emitter;
        }

        InterviewRecord record;
        try {
            record = loadOwnedRecord(userId, recordId);
        } catch (RuntimeException e) {
            sendSseError(emitter, e.getMessage());
            return emitter;
        }

        List<ChatMessage> chatHistory = sessionStore.load(recordId);

        if (chatHistory == null) {
            try {
                emitter.send(JSON.toJSONString(Map.of("error", "session_expired")));
                emitter.complete();
            } catch (IOException e) {
            }
            return emitter;
        }

        // 1. RAG 检索（含已用原子黑名单，避免重复提问同一知识点）
        String position = record.getPosition() != null ? record.getPosition() : "common";

        // 构造增强检索 query：上一轮 AI 问题 + 用户当前回答
        String ragQuery = message;
        for (int i = chatHistory.size() - 1; i >= 0; i--) {
            if (chatHistory.get(i) instanceof AiMessage) {
                String lastAiText = ((AiMessage) chatHistory.get(i)).text();
                if (lastAiText != null && !lastAiText.isBlank()) {
                    ragQuery = lastAiText.length() > 300
                            ? lastAiText.substring(lastAiText.length() - 300) + " " + message
                            : lastAiText + " " + message;
                }
                break;
            }
        }

        // RAG 检索（通过数据库题库 + Qdrant 向量检索封装岗位分类过滤和已用原子黑名单）
        List<String> usedAtomIds = sessionStore.loadUsedAtoms(recordId);
        QuestionBankSearchRequest searchRequest = new QuestionBankSearchRequest();
        searchRequest.setPosition(position);
        searchRequest.setQuery(ragQuery);
        searchRequest.setExcludeAtomIds(usedAtomIds);
        searchRequest.setLimit(3);
        List<QuestionBankSearchResult> retrievedResults;
        try {
            retrievedResults = questionBankService.search(searchRequest);
        } catch (Exception e) {
            log.warn("RAG 检索失败，跳过题库上下文: recordId={}, position={}, error={}",
                    recordId, position, e.getMessage());
            recordSystemEvent(userId, "RAG_RETRIEVAL_FAILED", "system",
                    Map.of("recordId", recordId, "position", position), false, e.getMessage());
            retrievedResults = List.of();
        }

        // 原子追加新命中原子 ID，避免并发覆盖
        List<String> newAtomIds = new ArrayList<>();
        for (QuestionBankSearchResult result : retrievedResults) {
            if (result.getAtomId() != null) newAtomIds.add(result.getAtomId());
        }
        sessionStore.addUsedAtoms(recordId, newAtomIds);

        // 持久化 RAG 检索日志
        int turnIdx = chatHistory.size() / 2 + 1;
        int rank = 0;
        for (QuestionBankSearchResult result : retrievedResults) {
            rank++;
            String atomId = result.getAtomId();
            if (atomId == null) continue;
            RagRetrievalLog logEntry = new RagRetrievalLog();
            logEntry.setUserId(userId);
            logEntry.setRecordId(recordId);
            logEntry.setTurnIndex(turnIdx);
            logEntry.setQueryText(ragQuery.length() > 500 ? ragQuery.substring(0, 500) : ragQuery);
            logEntry.setPosition(position);
            logEntry.setRetrievedAtomId(atomId);
            logEntry.setRetrievedCategory(result.getCategory());
            logEntry.setSimilarityScore(result.getScore());
            logEntry.setRankIndex(rank);
            try {
                ragRetrievalLogMapper.insert(logEntry);
            } catch (Exception e) {
                log.warn("RAG 检索日志写入失败: {}", e.getMessage());
            }
        }

        StringBuilder contextBuilder = new StringBuilder();
        for (int i = 0; i < retrievedResults.size(); i++) {
            QuestionBankSearchResult result = retrievedResults.get(i);
            contextBuilder.append(i + 1).append(". [atom_id: ")
                    .append(result.getAtomId() != null ? result.getAtomId() : "unknown")
                    .append("]\n").append(result.getPromptContext()).append("\n\n");
        }

        // 2. 根据显式面试阶段选择 Agent 人设（替代隐式 turn 计算）
        List<String> tailoredQuestions = sessionStore.loadTailoredQuestions(recordId);
        InterviewTurnPlanner.InterviewTurnPlan turnPlan =
                interviewTurnPlanner.plan(record, chatHistory, contextBuilder.toString(), tailoredQuestions);
        record.setPhase(turnPlan.phase().name());
        interviewRecordMapper.updateById(record);

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

                    emitter.send(JSON.toJSONString(Map.of("done", "true")));
                    emitter.complete();
                } catch (IOException e) {
                    emitter.completeWithError(e);
                }
            }

            @Override
            public void onError(Throwable error) {
                log.error("AI 响应错误: ", error);
                recordSystemEvent(userId, "DEEPSEEK_STREAM_FAILED", "system",
                        Map.of("recordId", recordId), false, error.getMessage());
                try {
                    emitter.send(JSON.toJSONString(Map.of("error", error.getMessage())));
                    emitter.complete();
                } catch (IOException e) {
                }
            }
        });

        return emitter;
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
            record.setChatHistory(JSON.toJSONString(historyMessages));
        } else {
            log.warn("缓存中无会话 (recordId={})，尝试从数据库恢复", recordId);
            String savedHistory = record.getChatHistory();
            if (savedHistory != null && !savedHistory.equals("[]")) {
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
                        if (text != null) {
                            if ("AI".equals(type))
                                historyMessages.add(new AiMessage(text));
                            else if ("USER".equals(type))
                                historyMessages.add(new UserMessage(text));
                            else if ("SYSTEM".equals(type))
                                historyMessages.add(new SystemMessage(text));
                        }
                    }
                    log.info("从数据库恢复了 {} 条对话消息", historyMessages.size());
                } catch (Exception e) {
                    log.error("解析数据库对话历史失败", e);
                }
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
        new Thread(() -> {
            try {
                mentorService.getInsight(uid);
                log.info("AI Mentor 缓存已更新 userId={}", uid);
            } catch (Exception e) {
                log.warn("AI Mentor 后台缓存更新失败 userId={}: {}", uid, e.getMessage());
            }
        }, "mentor-cache-" + uid).start();

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

    private void consumeQuota(Long userId, String quotaType) {
        if (usageQuotaService != null) {
            usageQuotaService.consume(userId, quotaType);
        }
    }

    private void recordSystemEvent(Long userId, String eventType, String category,
                                   Map<String, Object> metadata, boolean success, String errorMessage) {
        if (appEventService != null) {
            appEventService.recordSystemEvent(userId, eventType, category, metadata, success, errorMessage);
        }
    }
}
