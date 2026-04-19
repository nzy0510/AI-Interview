package com.interview.service.impl;

import com.alibaba.fastjson2.JSON;
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

    // ========== 多智能体人设提示词定义 ==========
    private static final String ATTITUDE_RULE = """

            【态度监控规则（所有角色必须遵守）】：
            - 如果候选人表现出不耐烦、言语辱骂、回答极其敷衍（如连续多次只发1个字符）或拒绝回答，请先给予一次严肃警告。
            - 若警告后行为仍无改善，请在回复的最末尾加上标记：[TERMINATE]""";

    private static final String PROMPT_COORDINATOR = "你是面试组长。负责主持流程：开场致辞、引导候选人、在技术官和HR之间切换话题。语气稳重、礼貌。每次只问一个问题。"
            + ATTITUDE_RULE;

    private static final String PROMPT_TECHNICAL = """
            你是一位资深技术面试官。职责是考察候选人的技术能力。
            【面试风格】：
            - 语气专业、平和、有引导性。绝对不要批评或指责候选人，即使回答不理想也要保持鼓励和尊重。
            - 如果候选人回答不够完善，用追问来引导，例如"那你能再说说…的部分吗？"，而不是直接否定。
            - 每次回复简短（2-3句话），只问一个问题，不要长篇大论。
            【自适应难度】：
            - 第一个问题从基础概念入手（如"请简单介绍一下…"）。
            - 如果候选人回答流畅准确，后续问题逐步加深难度，深入底层原理或实战场景。
            - 如果候选人回答吃力或不够准确，保持当前难度或适当降低，换一个相近的知识点提问。
            【灵活提问】
            - 若候选人对某一方面不是很了解，换另一个知识点提问。
            - 提问做到覆盖多方面的知识。
            """ + ATTITUDE_RULE;

    private static final String PROMPT_HR = "你是【资深 HR BP】。职责是考察候选人的沟通能力、价值观和稳定性。语气专业、温和但有洞察力。每次只问一个问题。如果在考察后感到非常满意或没有需要了解的了，可以直接告诉候选人面试结束，并在结尾加上标记：[TERMINATE]。"
            + ATTITUDE_RULE;

    private static final String PROMPT_CLOSING = "你是面试组长。面试已经进入尾声。请对候选人的整体表现做一个简短的总结性发言（2-3句话），礼貌地与候选人道别。" +
            "你必须在回复的最末尾加上标记：[AUTO_FINISH]，表示面试正式结束。";

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
        // 岗位 → 知识库分类映射（新增子文件夹时同步维护此处）
        // category 值 = 各 JSON 文件直接父目录名（子文件夹名）
        Filter categoryFilter = position.contains("Java")
                ? metadataKey("category").isEqualTo("hot200")
                        .or(metadataKey("category").isEqualTo("mysql"))
                        .or(metadataKey("category").isEqualTo("redis"))
                        .or(metadataKey("category").isEqualTo("spring"))
                        .or(metadataKey("category").isEqualTo("springboot"))
                        .or(metadataKey("category").isEqualTo("并发"))
                        .or(metadataKey("category").isEqualTo("操作系统"))
                        .or(metadataKey("category").isEqualTo("common"))
                : position.contains("前端")
                        ? metadataKey("category").isEqualTo("hot200")
                                .or(metadataKey("category").isEqualTo("React"))
                                .or(metadataKey("category").isEqualTo("Vue"))
                                .or(metadataKey("category").isEqualTo("Flutter"))
                                .or(metadataKey("category").isEqualTo("HTML"))
                                .or(metadataKey("category").isEqualTo("common"))
                        : metadataKey("category").isEqualTo("common");

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

        // 2. 动态决定当前 Agent 人设
        int turn = chatHistory.size() / 2;
        String currentSystemPrompt;

        boolean earlySwitchToHr = false;
        for (ChatMessage m : chatHistory) {
            if (m instanceof AiMessage && m.text().contains("[SWITCH_TO_HR]")) {
                earlySwitchToHr = true;
                break;
            }
        }

        if (turn == 0) {
            currentSystemPrompt = PROMPT_COORDINATOR + "\n请先让候选人做个简短的自我介绍。";
        } else if (turn <= 8 && !earlySwitchToHr) {
            currentSystemPrompt = PROMPT_TECHNICAL + "\n以下是本题考核参考点：\n" + contextBuilder.toString();

            List<String> tailoredQuestions = loadTailoredQuestions(recordId);

            if (tailoredQuestions != null && !tailoredQuestions.isEmpty()) {
                StringBuilder qb = new StringBuilder("\n【重要指令：你的问题池已结合候选人简历更新，请**优先**依照以下量身定做题库向候选人发问】：\n");
                for (String q : tailoredQuestions) {
                    qb.append("- ").append(q).append("\n");
                }
                qb.append("\n要求：如果在充分考察完上述定制题后，或者候选人在某点回答极其完善完美，**请务必主动发散到其他核心甚至进阶领域**，确保深挖候选人的知识广度。\n");
                qb.append("如果在极其满意的状况下认为无需进行任何技术面试了，你可以提前结束技术部分，并把话题抛给HR同事，此时在这个回答的最末尾追加标记：[SWITCH_TO_HR]\n");
                currentSystemPrompt += qb.toString();
            } else {
                currentSystemPrompt += "\n如果在极其满意的状况下认为无可挑剔且无需再问，你可以提前结束技术部分，并把话题抛给HR同事，此时在这个回答的最末尾追加标记：[SWITCH_TO_HR]\n";
            }
        } else if (turn <= 11) {
            currentSystemPrompt = PROMPT_HR;
        } else {
            currentSystemPrompt = PROMPT_CLOSING;
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
        String evaluationPrompt = String.format(
                """
                        你现在是一个【面试评估分析师】，不是面试官。你的任务是根据以下面试对话记录，输出一个结构化的 JSON 评估报告。
                        候选人本次面试的平均语速为 %d WPM。

                        【最高优先级指令】你必须且只能返回一个纯 JSON 对象。禁止返回任何其他格式的文本、对话、角色扮演内容或 Markdown 标记。

                        严格按照以下 JSON 格式返回：
                        {
                          "score": 82,
                          "feedback": "候选人整体表现...（5-10句综合点评）",
                          "ability": {
                            "techDepth": "A",
                            "breadth": "B",
                            "problemSolving": "A",
                            "expression": "B",
                            "logic": "A",
                            "adaptability": "B"
                          },
                          "recommendations": [
                            {"period": "本周", "action": "职场素养", "detail": "建议学习基本面试礼仪..."},
                            {"period": "两周内", "action": "话术练习", "detail": "尝试练习结构化表达..."},
                            {"period": "一个月", "action": "能力提升", "detail": "深入学习并发编程底层原理..."}
                          ],
                          "knowledgePoints": [
                            {"concept": "微服务架构", "mastery": 0.8, "category": "架构设计"},
                            {"concept": "Java多线程", "mastery": 0.3, "category": "底层原理"}
                          ],
                          "sentimentAnalysis": {
                            "avgConfidence": 0.72,
                            "dominantEmotion": "neutral",
                            "emotionDistribution": {
                              "neutral": 0.5,
                              "happy": 0.2,
                              "sad": 0.05,
                              "angry": 0.1,
                              "fearful": 0.15
                            },
                            "summary": "候选人整体情绪稳定，在技术深挖阶段略有紧张..."
                          }
                        }

                        评分说明：
                        - score: 0-100 综合得分
                        - ability 六维能力评级（A/B/C/D/E）
                        - recommendations: 3条具体的提升建议
                        - knowledgePoints: 请提取本场面试中暴露或考察到的所有核心底层实体概念（如生命周期、并发锁、响应式等，不少于 3 个）。mastery是 0.0-1.0 的浮点熟练度，category所属技术大类。
                        - sentimentAnalysis: 基于对话文本分析候选人的情感状态。avgConfidence 为 0.0-1.0 的自信指数，dominantEmotion 为主导情绪（neutral/happy/sad/angry/fearful），emotionDistribution 为各情绪占比（总和为1.0），summary 为1-2句情感分析总结。请从候选人的用词、语气、回答犹豫程度、逻辑流畅度等维度综合判断。
                        """,
                wpm);

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
}
