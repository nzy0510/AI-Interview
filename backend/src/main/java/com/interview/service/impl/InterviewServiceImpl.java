package com.interview.service.impl;

import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.interview.entity.InterviewPhase;
import com.interview.entity.InterviewRecord;
import com.interview.entity.RagRetrievalLog;
import com.interview.mapper.InterviewRecordMapper;
import com.interview.mapper.RagRetrievalLogMapper;
import com.interview.service.InterviewService;
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

import dev.langchain4j.rag.content.Content;

@Service
@Slf4j
public class InterviewServiceImpl implements InterviewService {

    @Autowired
    private InterviewRecordMapper interviewRecordMapper;

    @Autowired
    private OpenAiStreamingChatModel streamingChatModel;

    @Autowired
    private com.interview.config.InterviewPrompts interviewPrompts;

    @Autowired
    private com.interview.service.SessionStore sessionStore;

    @Autowired
    private com.interview.service.RagRetriever ragRetriever;

    @Autowired
    private com.interview.service.EvaluationGenerator evaluationGenerator;

    @Autowired
    private RagRetrievalLogMapper ragRetrievalLogMapper;

    @Autowired
    private com.interview.service.MentorService mentorService;

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

        // RAG 检索（通过 RagRetriever 封装岗位分类过滤 + 已用原子黑名单）
        List<String> usedAtomIds = sessionStore.loadUsedAtoms(recordId);
        List<com.interview.service.RagRetriever.RetrievedContent> retrievedResults =
                ragRetriever.retrieveWithScores(position, ragQuery, usedAtomIds);
        List<Content> retrievedContents = new ArrayList<>();
        for (com.interview.service.RagRetriever.RetrievedContent result : retrievedResults) {
            retrievedContents.add(result.content());
        }

        // 原子追加新命中原子 ID，避免并发覆盖
        List<String> newAtomIds = new ArrayList<>();
        for (Content content : retrievedContents) {
            String atomId = content.textSegment().metadata().getString("id");
            if (atomId != null) newAtomIds.add(atomId);
        }
        sessionStore.addUsedAtoms(recordId, newAtomIds);

        // 持久化 RAG 检索日志
        int turnIdx = chatHistory.size() / 2 + 1;
        int rank = 0;
        for (com.interview.service.RagRetriever.RetrievedContent result : retrievedResults) {
            Content content = result.content();
            rank++;
            String atomId = content.textSegment().metadata().getString("id");
            if (atomId == null) continue;
            String category = content.textSegment().metadata().getString("category");
            RagRetrievalLog logEntry = new RagRetrievalLog();
            logEntry.setUserId(userId);
            logEntry.setRecordId(recordId);
            logEntry.setTurnIndex(turnIdx);
            logEntry.setQueryText(ragQuery.length() > 500 ? ragQuery.substring(0, 500) : ragQuery);
            logEntry.setPosition(position);
            logEntry.setRetrievedAtomId(atomId);
            logEntry.setRetrievedCategory(category);
            logEntry.setSimilarityScore(result.score() != null ? result.score() : 0.0);
            logEntry.setRankIndex(rank);
            try {
                ragRetrievalLogMapper.insert(logEntry);
            } catch (Exception e) {
                log.warn("RAG 检索日志写入失败: {}", e.getMessage());
            }
        }

        StringBuilder contextBuilder = new StringBuilder();
        for (int i = 0; i < retrievedContents.size(); i++) {
            Content content = retrievedContents.get(i);
            String atomId = content.textSegment().metadata().getString("id");
            contextBuilder.append(i + 1).append(". [atom_id: ")
                    .append(atomId != null ? atomId : "unknown")
                    .append("]\n").append(content.textSegment().text()).append("\n\n");
        }

        // 2. 根据显式面试阶段选择 Agent 人设（替代隐式 turn 计算）
        int turn = chatHistory.size() / 2;
        String currentPhase = record.getPhase();
        if (currentPhase == null || currentPhase.isEmpty()) {
            currentPhase = InterviewPhase.OPENING.name();
        }

        // 检测上一轮 AI 回复中的阶段切换标记
        boolean switchToHrMarker = false;
        boolean autoFinishMarker = false;
        for (ChatMessage m : chatHistory) {
            if (m instanceof AiMessage) {
                String text = m.text();
                if (text.contains("[SWITCH_TO_HR]")) switchToHrMarker = true;
                if (text.contains("[AUTO_FINISH]")) autoFinishMarker = true;
            }
        }

        // 状态转换
        InterviewPhase phase = determineNextPhase(
                InterviewPhase.valueOf(currentPhase), turn, switchToHrMarker, autoFinishMarker);
        record.setPhase(phase.name());
        interviewRecordMapper.updateById(record);

        // 根据阶段构建 System Prompt
        String currentSystemPrompt;
        String setupInstructions = buildSetupInstructions(record);
        if (phase == InterviewPhase.OPENING) {
            currentSystemPrompt = interviewPrompts.getCoordinator() + "\n" + interviewPrompts.getAttitudeRule()
                    + setupInstructions
                    + "\n请先让候选人做个简短的自我介绍。";
        } else if (phase == InterviewPhase.TECHNICAL) {
            currentSystemPrompt = interviewPrompts.getTechnical() + "\n" + interviewPrompts.getAttitudeRule()
                    + setupInstructions
                    + "\n候选知识点（选择最相关的1个追问，不要直接说出标准答案，优先追问候选人回答中缺失的部分）：\n"
                    + contextBuilder.toString();

            List<String> tailoredQuestions = sessionStore.loadTailoredQuestions(recordId);
            if (tailoredQuestions != null && !tailoredQuestions.isEmpty()) {
                StringBuilder qb = new StringBuilder("\n【重要指令：你的问题池已结合候选人简历更新，请**优先**依照以下量身定做题库向候选人发问】：\n");
                for (String q : tailoredQuestions) qb.append("- ").append(q).append("\n");
                qb.append("\n要求：如果在充分考察完上述定制题后，或者候选人在某点回答极其完善完美，**请务必主动发散到其他核心甚至进阶领域**，确保深挖候选人的知识广度。\n");
                qb.append("如果在极其满意的状况下认为无需进行任何技术面试了，你可以提前结束技术部分，并把话题抛给HR同事，此时在这个回答的最末尾追加标记：[SWITCH_TO_HR]\n");
                currentSystemPrompt += qb.toString();
            } else {
                currentSystemPrompt += "\n如果在极其满意的状况下认为无可挑剔且无需再问，你可以提前结束技术部分，并把话题抛给HR同事，此时在这个回答的最末尾追加标记：[SWITCH_TO_HR]\n";
            }
        } else if (phase == InterviewPhase.HR) {
            currentSystemPrompt = interviewPrompts.getHr() + "\n" + interviewPrompts.getAttitudeRule()
                    + setupInstructions;
        } else {
            currentSystemPrompt = interviewPrompts.getClosing() + setupInstructions;
        }

        // 3. 构造消息列表
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(new SystemMessage(currentSystemPrompt));
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

    /** 显式状态机：根据当前阶段、轮次和 AI 标记决定下一阶段 */
    private InterviewPhase determineNextPhase(InterviewPhase current, int turn,
                                             boolean switchToHrMarker, boolean autoFinishMarker) {
        if (current == InterviewPhase.FINISHED) return InterviewPhase.FINISHED;
        if (autoFinishMarker) return InterviewPhase.FINISHED;
        if (current == InterviewPhase.OPENING && turn >= 1) return InterviewPhase.TECHNICAL;
        if (current == InterviewPhase.TECHNICAL) {
            if (switchToHrMarker || turn > 8) return InterviewPhase.HR;
            return InterviewPhase.TECHNICAL;
        }
        if (current == InterviewPhase.HR && turn > 11) return InterviewPhase.CLOSING;
        return current;
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

    private String buildSetupInstructions(InterviewRecord record) {
        StringBuilder sb = new StringBuilder("\n【候选人本场面试配置】\n");
        sb.append("- 难度倾向：").append(humanDifficulty(record.getDifficultyLevel())).append("\n");
        List<String> focusAreas = parseFocusAreas(record.getFocusAreas());
        if (!focusAreas.isEmpty()) {
            sb.append("- 重点能力：").append(String.join("、", focusAreas)).append("\n");
            sb.append("- 提问策略：优先围绕重点能力设计追问，但不要牺牲岗位核心知识覆盖。\n");
        }
        sb.append("- 难度策略：根据难度倾向调整追问深度、案例复杂度和容错标准。\n");
        return sb.toString();
    }

    private String humanDifficulty(String difficultyLevel) {
        return switch (normalizeDifficulty(difficultyLevel)) {
            case "junior" -> "应届/0-1年，重视基础概念、表达清晰度和项目参与度";
            case "senior" -> "3-5年，重视系统设计、复杂问题定位和技术取舍";
            case "principal" -> "5年+，重视架构判断、跨团队影响力和深层原理";
            default -> "1-3年，重视核心原理、业务落地和独立解决问题能力";
        };
    }

    private List<String> parseFocusAreas(String raw) {
        if (raw == null || raw.isBlank()) return new ArrayList<>();
        try {
            List<String> values = JSON.parseArray(raw, String.class);
            List<String> labels = new ArrayList<>();
            for (String value : values) {
                labels.add(humanFocusArea(value));
            }
            return labels;
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    private String humanFocusArea(String focusArea) {
        return switch (focusArea) {
            case "projects" -> "项目经历深挖";
            case "depth" -> "技术原理深度";
            case "architecture" -> "系统设计与架构";
            case "algorithm" -> "算法基础与思维";
            case "communication" -> "表达沟通与协作";
            case "pressure" -> "压力应对与稳定性";
            default -> focusArea;
        };
    }

    private void sendSseError(SseEmitter emitter, String message) {
        try {
            emitter.send(JSON.toJSONString(Map.of("error", message)));
            emitter.complete();
        } catch (IOException ignored) {
        }
    }
}
