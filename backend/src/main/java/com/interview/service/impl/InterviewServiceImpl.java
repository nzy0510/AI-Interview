package com.interview.service.impl;

import com.alibaba.fastjson2.JSON;
import com.interview.entity.InterviewPhase;
import com.interview.entity.InterviewRecord;
import com.interview.mapper.InterviewRecordMapper;
import com.interview.service.InterviewService;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;

import dev.langchain4j.model.StreamingResponseHandler;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import dev.langchain4j.model.output.Response;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.content.retriever.EmbeddingStoreContentRetriever;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.store.embedding.filter.Filter;
import static dev.langchain4j.store.embedding.filter.MetadataFilterBuilder.metadataKey;
import dev.langchain4j.rag.query.Query;

@Service
@Slf4j
public class InterviewServiceImpl implements InterviewService {

    @Autowired
    private InterviewRecordMapper interviewRecordMapper;

    @Autowired
    private OpenAiStreamingChatModel streamingChatModel;

    @Autowired
    private OpenAiChatModel chatModel;

    @Autowired
    private EmbeddingStore<TextSegment> embeddingStore;

    @Autowired
    private EmbeddingModel embeddingModel;

    @Autowired(required = false)
    private RedisTemplate<String, Object> redisTemplate;

    @Autowired
    private com.interview.config.PositionCategoryConfig positionCategoryConfig;

    @Autowired
    private com.interview.config.InterviewPrompts interviewPrompts;

    // Redis key 前缀
    private static final String CHAT_KEY_PREFIX = "interview:chat:";
    private static final String TAILORED_KEY_PREFIX = "interview:tailored:";
    private static final String USED_ATOMS_KEY_PREFIX = "interview:used_atoms:"; // 已用知识原子黑名单
    private static final long SESSION_TTL_HOURS = 2;

    // 本地兜底缓存（Redis 不可用时自动降级到内存）
    private final Map<Long, List<ChatMessage>> localChatCache = new ConcurrentHashMap<>();
    private final Map<Long, List<String>> localTailoredCache = new ConcurrentHashMap<>();
    private final Map<Long, List<String>> localUsedAtomsCache = new ConcurrentHashMap<>(); // 已用原子黑名单
    private volatile boolean redisAvailable = true; // 标记 Redis 是否可连通

    // ========== 双模式会话存储工具（Redis 优先，内存兜底） ==========

    private boolean isRedisReady() {
        if (redisTemplate == null)
            return false;
        if (!redisAvailable)
            return false;
        try {
            redisTemplate.opsForValue().get("__ping__");
            return true;
        } catch (Exception e) {
            if (redisAvailable) {
                log.warn("⚠️ Redis 不可用，已自动降级到内存缓存模式: {}", e.getMessage());
                redisAvailable = false;
            }
            return false;
        }
    }

    private void saveChatMessages(Long recordId, List<ChatMessage> messages) {
        // 始终保留内存副本
        localChatCache.put(recordId, new ArrayList<>(messages));
        // 尝试同步到 Redis
        if (isRedisReady()) {
            try {
                List<Map<String, String>> serialized = new ArrayList<>();
                for (ChatMessage msg : messages) {
                    Map<String, String> m = new HashMap<>();
                    if (msg instanceof UserMessage) {
                        m.put("type", "USER");
                        m.put("text", ((UserMessage) msg).singleText());
                    } else if (msg instanceof AiMessage) {
                        m.put("type", "AI");
                        m.put("text", ((AiMessage) msg).text());
                    } else if (msg instanceof SystemMessage) {
                        m.put("type", "SYSTEM");
                        m.put("text", ((SystemMessage) msg).text());
                    }
                    serialized.add(m);
                }
                redisTemplate.opsForValue().set(CHAT_KEY_PREFIX + recordId, JSON.toJSONString(serialized),
                        SESSION_TTL_HOURS, TimeUnit.HOURS);
            } catch (Exception e) {
                log.trace("Redis 写入跳过: {}", e.getMessage());
            }
        }
    }

    private List<ChatMessage> loadChatMessages(Long recordId) {
        // 优先从 Redis 读
        if (isRedisReady()) {
            try {
                Object raw = redisTemplate.opsForValue().get(CHAT_KEY_PREFIX + recordId);
                if (raw != null) {
                    String json = raw instanceof String ? (String) raw : JSON.toJSONString(raw);
                    com.alibaba.fastjson2.JSONArray arr = JSON.parseArray(json);
                    List<ChatMessage> messages = new ArrayList<>();
                    for (int i = 0; i < arr.size(); i++) {
                        com.alibaba.fastjson2.JSONObject obj = arr.getJSONObject(i);
                        String type = obj.getString("type");
                        String text = obj.getString("text");
                        if (text == null)
                            continue;
                        switch (type) {
                            case "USER" -> messages.add(new UserMessage(text));
                            case "AI" -> messages.add(new AiMessage(text));
                            case "SYSTEM" -> messages.add(new SystemMessage(text));
                        }
                    }
                    localChatCache.put(recordId, new ArrayList<>(messages));
                    return messages;
                }
            } catch (Exception e) {
                log.trace("Redis 读取跳过: {}", e.getMessage());
            }
        }
        // 降级从内存取
        List<ChatMessage> cached = localChatCache.get(recordId);
        return cached != null ? new ArrayList<>(cached) : null;
    }

    private void deleteChatSession(Long recordId) {
        localChatCache.remove(recordId);
        localTailoredCache.remove(recordId);
        localUsedAtomsCache.remove(recordId);
        if (isRedisReady()) {
            try {
                redisTemplate.delete(CHAT_KEY_PREFIX + recordId);
                redisTemplate.delete(TAILORED_KEY_PREFIX + recordId);
                redisTemplate.delete(USED_ATOMS_KEY_PREFIX + recordId);
            } catch (Exception ignored) {
            }
        }
    }

    private void saveTailoredQuestions(Long recordId, List<String> questions) {
        localTailoredCache.put(recordId, questions);
        if (isRedisReady()) {
            try {
                redisTemplate.opsForValue().set(TAILORED_KEY_PREFIX + recordId, JSON.toJSONString(questions),
                        SESSION_TTL_HOURS, TimeUnit.HOURS);
            } catch (Exception ignored) {
            }
        }
    }

    private List<String> loadTailoredQuestions(Long recordId) {
        if (isRedisReady()) {
            try {
                Object raw = redisTemplate.opsForValue().get(TAILORED_KEY_PREFIX + recordId);
                if (raw != null) {
                    String json = raw instanceof String ? (String) raw : JSON.toJSONString(raw);
                    return JSON.parseArray(json, String.class);
                }
            } catch (Exception ignored) {
            }
        }
        return localTailoredCache.get(recordId);
    }

    /** 保存本轮面试已使用的知识原子 ID 集合（双模缓存） */
    private void saveUsedAtoms(Long recordId, List<String> usedIds) {
        localUsedAtomsCache.put(recordId, new ArrayList<>(usedIds));
        if (isRedisReady()) {
            try {
                redisTemplate.opsForValue().set(
                        USED_ATOMS_KEY_PREFIX + recordId,
                        JSON.toJSONString(usedIds),
                        SESSION_TTL_HOURS, TimeUnit.HOURS);
            } catch (Exception ignored) {
            }
        }
    }

    /** 读取本轮面试已使用的知识原子 ID 集合（双模缓存） */
    private List<String> loadUsedAtoms(Long recordId) {
        if (isRedisReady()) {
            try {
                Object raw = redisTemplate.opsForValue().get(USED_ATOMS_KEY_PREFIX + recordId);
                if (raw != null) {
                    String json = raw instanceof String ? (String) raw : JSON.toJSONString(raw);
                    List<String> ids = JSON.parseArray(json, String.class);
                    localUsedAtomsCache.put(recordId, ids);
                    return ids;
                }
            } catch (Exception ignored) {
            }
        }
        List<String> cached = localUsedAtomsCache.get(recordId);
        return cached != null ? new ArrayList<>(cached) : new ArrayList<>();
    }

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
        InterviewRecord record = new InterviewRecord();
        record.setUserId(userId);
        record.setPosition(position);
        record.setPhase(InterviewPhase.OPENING.name());
        record.setInterviewMode(mode != null ? mode : "text");
        record.setCreateTime(LocalDateTime.now());
        record.setChatHistory("[]");
        interviewRecordMapper.insert(record);

        saveChatMessages(record.getId(), new ArrayList<>());

        if (resumeQuestions != null && !resumeQuestions.isEmpty()) {
            saveTailoredQuestions(record.getId(), resumeQuestions);
        }

        return record.getId();
    }

    @Override
    public SseEmitter chatStream(Long userId, Long recordId, String message) {
        SseEmitter emitter = new SseEmitter(0L);

        List<ChatMessage> chatHistory = loadChatMessages(recordId);

        if (chatHistory == null) {
            try {
                emitter.send(JSON.toJSONString(Map.of("error", "session_expired")));
                emitter.complete();
            } catch (IOException e) {
            }
            return emitter;
        }

        // 1. RAG 检索（含已用原子黑名单，避免重复提问同一知识点）
        InterviewRecord record = interviewRecordMapper.selectById(recordId);
        String position = record != null ? record.getPosition() : "common";
        // 岗位 → 知识库分类映射（通过 application.yml 配置，新增分类无需改代码）
        java.util.List<String> categories = positionCategoryConfig.getCategoriesFor(position);
        Filter categoryFilter = metadataKey("category").isEqualTo(categories.get(0));
        for (int i = 1; i < categories.size(); i++) {
            categoryFilter = categoryFilter.or(metadataKey("category").isEqualTo(categories.get(i)));
        }

        // 加载已用原子黑名单，构建排除 Filter（避免同一知识点被重复追问）
        List<String> usedAtomIds = loadUsedAtoms(recordId);
        Filter finalFilter = usedAtomIds.isEmpty()
                ? categoryFilter
                : categoryFilter.and(metadataKey("id").isNotIn(usedAtomIds));

        ContentRetriever contentRetriever = EmbeddingStoreContentRetriever.builder()
                .embeddingStore(embeddingStore).embeddingModel(embeddingModel).filter(finalFilter).maxResults(3)
                .minScore(0.6).build();

        List<Content> retrievedContents = (message != null && message.trim().length() > 2)
                ? contentRetriever.retrieve(Query.from(message))
                : new ArrayList<>();

        // 将本轮检索到的原子 ID 记入黑名单
        for (Content content : retrievedContents) {
            String atomId = content.textSegment().metadata().getString("id");
            if (atomId != null && !usedAtomIds.contains(atomId)) {
                usedAtomIds.add(atomId);
            }
        }
        saveUsedAtoms(recordId, usedAtomIds);

        StringBuilder contextBuilder = new StringBuilder();
        for (Content content : retrievedContents)
            contextBuilder.append("- ").append(content.textSegment().text()).append("\n");

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
        if (phase == InterviewPhase.OPENING) {
            currentSystemPrompt = interviewPrompts.getCoordinator() + "\n" + interviewPrompts.getAttitudeRule()
                    + "\n请先让候选人做个简短的自我介绍。";
        } else if (phase == InterviewPhase.TECHNICAL) {
            currentSystemPrompt = interviewPrompts.getTechnical() + "\n" + interviewPrompts.getAttitudeRule()
                    + "\n以下是本题考核参考点：\n" + contextBuilder.toString();

            List<String> tailoredQuestions = loadTailoredQuestions(recordId);
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
            currentSystemPrompt = interviewPrompts.getHr() + "\n" + interviewPrompts.getAttitudeRule();
        } else {
            currentSystemPrompt = interviewPrompts.getClosing();
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
                    saveChatMessages(recordId, currentHistory);

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
        List<ChatMessage> historyMessages = loadChatMessages(recordId);
        deleteChatSession(recordId);

        InterviewRecord record = interviewRecordMapper.selectById(recordId);

        if (record == null) {
            log.error("面试记录不存在: recordId={}", recordId);
            return null;
        }

        record.setEndTime(LocalDateTime.now());
        record.setPhase(InterviewPhase.FINISHED.name());
        record.setVoiceWpm(wpm != null ? wpm : 0);
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
        String evaluationPrompt = String.format(interviewPrompts.getEvaluation(), wpm);

        List<ChatMessage> evalMessages = new ArrayList<>();
        evalMessages.add(new SystemMessage(evaluationPrompt));
        for (ChatMessage msg : historyMessages) {
            if (!(msg instanceof SystemMessage)) {
                evalMessages.add(msg);
            }
        }
        evalMessages.add(new UserMessage("面试已结束。请立即输出 JSON 格式的评估报告，不要输出任何其他内容。"));

        try {
            log.info("开始生成 AI 评估报告 (recordId={}, 对话轮数={})", recordId, historyMessages.size());
            Response<AiMessage> evalResponse = chatModel.generate(evalMessages);
            String raw = evalResponse.content().text().replace("```json", "").replace("```", "").trim();
            log.info("AI 评估原始响应: {}", raw);

            com.alibaba.fastjson2.JSONObject evalObj = JSON.parseObject(raw);
            record.setScore(evalObj.getInteger("score") != null ? evalObj.getInteger("score") : 0);
            record.setFeedback(evalObj.getString("feedback") != null ? evalObj.getString("feedback") : "评估内容生成异常");
            if (evalObj.get("ability") != null)
                record.setAbilityJson(evalObj.getJSONObject("ability").toJSONString());
            if (evalObj.get("recommendations") != null)
                record.setRecommendations(evalObj.getJSONArray("recommendations").toJSONString());
            if (evalObj.get("knowledgePoints") != null)
                record.setKnowledgeJson(evalObj.getJSONArray("knowledgePoints").toJSONString());
            // 文本情感分析：如果没有视频情感数据，使用 AI 基于对话文本的情感分析
            if ((record.getEmotionJson() == null || record.getEmotionJson().isEmpty())
                    && evalObj.get("sentimentAnalysis") != null) {
                com.alibaba.fastjson2.JSONObject sentiment = evalObj.getJSONObject("sentimentAnalysis");
                sentiment.put("source", "text");
                record.setEmotionJson(sentiment.toJSONString());
            } else if (record.getEmotionJson() != null && !record.getEmotionJson().isEmpty()
                    && evalObj.get("sentimentAnalysis") != null) {
                // 视频模式：将 AI 的文本情感总结追加到已有的视频情感数据中
                try {
                    com.alibaba.fastjson2.JSONObject existing = JSON.parseObject(record.getEmotionJson());
                    existing.put("source", "video");
                    String summary = evalObj.getJSONObject("sentimentAnalysis").getString("summary");
                    if (summary != null) existing.put("summary", summary);
                    record.setEmotionJson(existing.toJSONString());
                } catch (Exception ignored) {}
            }
        } catch (Exception e) {
            log.error("评估生成失败 (recordId={})", recordId, e);
            record.setScore(0);
            record.setFeedback("AI 评估生成异常: " + e.getMessage());
        }

        interviewRecordMapper.updateById(record);
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
}
