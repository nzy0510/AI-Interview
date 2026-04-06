package com.interview.service.impl;

import com.alibaba.fastjson2.JSON;
import com.interview.entity.InterviewRecord;
import com.interview.mapper.InterviewRecordMapper;
import com.interview.service.InterviewService;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.StreamingResponseHandler;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import dev.langchain4j.model.output.Response;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

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

    // 活跃会话记忆
    private final Map<Long, ChatMemory> chatMemories = new ConcurrentHashMap<>();

    // ========== 多智能体人设提示词定义 ==========
    // 通用态度监控规则（注入到每个 Agent 的提示词末尾）
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

    private static final String PROMPT_HR = "你是【资深 HR BP】。职责是考察候选人的沟通能力、价值观和稳定性。语气专业、温和但有洞察力。每次只问一个问题。" + ATTITUDE_RULE;

    private static final String PROMPT_CLOSING = "你是面试组长。面试已经进入尾声。请对候选人的整体表现做一个简短的总结性发言（2-3句话），礼貌地与候选人道别。" +
            "你必须在回复的最末尾加上标记：[TERMINATE]，表示面试正式结束。";

    @Override
    public Long startInterview(Long userId, String position) {
        return startInterview(userId, position, "text");
    }

    @Override
    public Long startInterview(Long userId, String position, String mode) {
        InterviewRecord record = new InterviewRecord();
        record.setUserId(userId);
        record.setPosition(position);
        record.setInterviewMode(mode != null ? mode : "text");
        record.setCreateTime(LocalDateTime.now());
        record.setChatHistory("[]");
        interviewRecordMapper.insert(record);

        chatMemories.put(record.getId(), MessageWindowChatMemory.withMaxMessages(20));
        return record.getId();
    }

    @Override
    public SseEmitter chatStream(Long userId, Long recordId, String message) {
        SseEmitter emitter = new SseEmitter(0L);
        ChatMemory chatMemory = chatMemories.get(recordId);

        if (chatMemory == null) {
            try {
                emitter.send(JSON.toJSONString(Map.of("error", "session_expired")));
                emitter.complete();
            } catch (IOException e) {
            }
            return emitter;
        }

        // 1. RAG 检索
        InterviewRecord record = interviewRecordMapper.selectById(recordId);
        String position = record != null ? record.getPosition() : "common";
        Filter categoryFilter = position.contains("Java")
                ? metadataKey("category").isEqualTo("java").or(metadataKey("category").isEqualTo("common"))
                : position.contains("前端")
                        ? metadataKey("category").isEqualTo("frontend").or(metadataKey("category").isEqualTo("common"))
                        : metadataKey("category").isEqualTo("common");

        ContentRetriever contentRetriever = EmbeddingStoreContentRetriever.builder()
                .embeddingStore(embeddingStore).embeddingModel(embeddingModel).filter(categoryFilter).maxResults(3)
                .minScore(0.6).build();

        List<Content> retrievedContents = (message != null && message.trim().length() > 2)
                ? contentRetriever.retrieve(Query.from(message))
                : new ArrayList<>();
        StringBuilder contextBuilder = new StringBuilder();
        for (Content content : retrievedContents)
            contextBuilder.append("- ").append(content.textSegment().text()).append("\n");

        // 2. 动态决定当前 Agent 人设
        int round = chatMemory.messages().size();
        String currentSystemPrompt;
        if (round == 0) {
            currentSystemPrompt = PROMPT_COORDINATOR + "\n请先让候选人做个简短的自我介绍。";
        } else if (round < 10) {
            currentSystemPrompt = PROMPT_TECHNICAL + "\n以下是本题考核参考点：\n" + contextBuilder.toString();
        } else if (round < 14) {
            currentSystemPrompt = PROMPT_HR;
        } else {
            // HR 阶段结束后，进入收尾阶段，AI 道别后自动触发 [TERMINATE]
            currentSystemPrompt = PROMPT_CLOSING;
        }

        // 3. 构造消息列表 (必须包含当前 Agent 的 SystemMessage)
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(new SystemMessage(currentSystemPrompt));
        messages.addAll(chatMemory.messages());
        messages.add(new UserMessage(message));

        // 4. SSE 流式输出处理 (底层调用，规避 Usage 解析 Bug)
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
                    // 即使 response 对象中的统计信息为空，我们也只用它的内容或自己拼接的内容
                    chatMemory.add(new UserMessage(message));
                    chatMemory.add(new AiMessage(aiResponseBuilder.toString()));
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
        ChatMemory chatMemory = chatMemories.remove(recordId);
        InterviewRecord record = interviewRecordMapper.selectById(recordId);

        if (record == null) {
            log.error("面试记录不存在: recordId={}", recordId);
            return null;
        }

        record.setEndTime(LocalDateTime.now());
        record.setVoiceWpm(wpm != null ? wpm : 0);
        // 保存视频面试的情感分析数据
        if (emotionJson != null && !emotionJson.isEmpty()) {
            record.setEmotionJson(emotionJson);
        }

        // ========== 构建评估用的对话历史 ==========
        // 优先使用内存中的 chatMemory；如果为 null（页面刷新等场景），则从数据库恢复
        List<ChatMessage> historyMessages = new ArrayList<>();
        if (chatMemory != null) {
            historyMessages.addAll(chatMemory.messages());
            // 同步对话历史到数据库
            record.setChatHistory(JSON.toJSONString(chatMemory.messages()));
        } else {
            log.warn("chatMemory 为空 (recordId={})，尝试从数据库恢复对话历史", recordId);
            // 从数据库中的 chatHistory JSON 恢复对话
            String savedHistory = record.getChatHistory();
            if (savedHistory != null && !savedHistory.equals("[]")) {
                try {
                    com.alibaba.fastjson2.JSONArray arr = JSON.parseArray(savedHistory);
                    for (int i = 0; i < arr.size(); i++) {
                        com.alibaba.fastjson2.JSONObject msgObj = arr.getJSONObject(i);
                        String type = msgObj.getString("type");
                        String text = msgObj.getString("text");
                        if (text == null) {
                            // langchain4j 序列化格式可能是 "contents" 或嵌套结构
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

        // 如果对话历史为空，无法进行评估
        if (historyMessages.isEmpty()) {
            log.warn("对话历史为空，跳过 AI 评估 (recordId={})", recordId);
            record.setScore(0);
            record.setFeedback("面试对话为空，无法生成评估报告。请确保面试过程中有完整的对话记录。");
            interviewRecordMapper.updateById(record);
            return record;
        }

        // ========== AI 评估：生成结构化 JSON 报告 ==========
        // 【关键修复】过滤掉对话历史中的 SystemMessage（角色扮演提示词），
        // 防止评估模型继续沉浸在"面试官"角色中，导致返回角色扮演文本而非 JSON
        String evaluationPrompt = String.format(
                """
                        你现在是一个【面试评估分析师】，不是面试官。你的任务是根据以下面试对话记录，输出一个结构化的 JSON 评估报告。
                        候选人本次面试的平均语速为 %d WPM。

                        【最高优先级指令】你必须且只能返回一个纯 JSON 对象。禁止返回任何其他格式的文本、对话、角色扮演内容或 Markdown 标记。

                        严格按照以下 JSON 格式返回：
                        {
                          "score": 82,
                          "feedback": "候选人整体表现...（3-5句综合点评）",
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
                          ]
                        }

                        评分说明：
                        - score: 0-100 综合得分
                        - ability 六维能力评级（A/B/C/D/E）
                        - recommendations: 3条具体的提升建议
                        - knowledgePoints: 请提取本场面试中暴露或考察到的所有核心底层实体概念（如生命周期、并发锁、响应式等，不少于 3 个）。mastery是 0.0-1.0 的浮点熟练度，category所属技术大类。
                        """,
                wpm);

        // 构建评估消息列表：用全新的 SystemMessage 确立"评估分析师"角色
        List<ChatMessage> evalMessages = new ArrayList<>();
        evalMessages.add(new SystemMessage(evaluationPrompt));
        // 只保留 UserMessage 和 AiMessage，过滤掉之前的角色扮演 SystemMessage
        for (ChatMessage msg : historyMessages) {
            if (!(msg instanceof SystemMessage)) {
                evalMessages.add(msg);
            }
        }
        // 追加一条 UserMessage 强化 JSON 输出要求
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
        } catch (Exception e) {
            log.error("评估生成失败 (recordId={})", recordId, e);
            record.setScore(0);
            record.setFeedback("AI 评估生成异常: " + e.getMessage());
        }

        interviewRecordMapper.updateById(record);
        return record;
    }

}
