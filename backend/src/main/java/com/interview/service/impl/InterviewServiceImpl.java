package com.interview.service.impl;

import com.alibaba.fastjson2.JSON;
import com.interview.entity.InterviewRecord;
import com.interview.mapper.InterviewRecordMapper;
import com.interview.service.InterviewService;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.StreamingResponseHandler;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import java.util.HashMap;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Map;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.content.retriever.EmbeddingStoreContentRetriever;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.store.embedding.filter.Filter;
import static dev.langchain4j.store.embedding.filter.MetadataFilterBuilder.metadataKey;
import dev.langchain4j.rag.query.Query;

@Service
public class InterviewServiceImpl implements InterviewService {

    @Autowired
    private InterviewRecordMapper interviewRecordMapper; // 面试记录数据库操作

    @Autowired
    private OpenAiStreamingChatModel streamingChatModel; // 流式聊天模型（用于 SSE 逐字输出）

    @Autowired
    private OpenAiChatModel chatModel; // 同步聊天模型（用于生成最终评价报告）

    @Autowired
    private EmbeddingStore<TextSegment> embeddingStore; // 内存级向量数据库，存储知识库的向量化文档片段

    @Autowired
    private EmbeddingModel embeddingModel; // 本地嵌入模型，将文本转化为多维向量（AllMiniLmL6V2）

    // 活跃会话的聊天记忆存储（key=面试记录ID，value=该场面试的多轮对话记忆）
    private final Map<Long, ChatMemory> chatMemories = new ConcurrentHashMap<>();

    /**
     * 开始面试：初始化数据库记录 + 创建 AI 聊天记忆 + 注入面试官角色提示词
     */
    @Override
    public Long startInterview(Long userId, String position) {
        // 1. 创建面试记录并写入数据库
        InterviewRecord record = new InterviewRecord();
        record.setUserId(userId);
        record.setPosition(position);
        record.setCreateTime(LocalDateTime.now());
        record.setChatHistory("[]"); // 初始化空的 JSON 数组，后续会存储完整对话历史
        interviewRecordMapper.insert(record);

        // 2. 初始化该场面试的聊天记忆窗口（最多保留最近 20 条消息，防止 Token 超限）
        ChatMemory chatMemory = MessageWindowChatMemory.withMaxMessages(20);

        // 3. 注入 SystemPrompt（角色扮演提示词），告诉 AI 它现在是一名面试官
        String systemPrompt = String.format(
                "你是一个专业的面试官，正在面试候选人【%s】岗位。请注意：\n" +
                        "1. 你的语气要专业、严肃但有礼貌。\n" +
                        "2. 每次只问一个问题。刚开始请让候选人做个简短的自我介绍。\n" +
                        "3. 根据候选人的回答深入追问技术细节或八股文知识。\n" +
                        "4. 尽量口语化，像真实的面试对话，不要长篇大论。",
                position);
        chatMemory.add(new SystemMessage(systemPrompt));
        chatMemories.put(record.getId(), chatMemory);

        return record.getId();
    }

    /**
     * SSE 流式聊天：接收用户消息 → RAG 检索知识库 → 增强提示词 → 流式输出 AI 回复
     * 这是 RAG（检索增强生成）的核心方法
     */
    @Override
    public SseEmitter chatStream(Long userId, Long recordId, String message) {
        SseEmitter emitter = new SseEmitter(0L); // 0 表示不设超时，保持 SSE 长连接

        // 获取该场面试的聊天记忆
        ChatMemory chatMemory = chatMemories.get(recordId);
        if (chatMemory == null) {
            try {
                Map<String, String> errMap = new HashMap<>();
                errMap.put("error", "session_expired");
                emitter.send(JSON.toJSONString(errMap));
                emitter.complete();
            } catch (IOException e) {
                emitter.completeWithError(e);
            }
            return emitter;
        }

        // ===== RAG 第一步：根据岗位构建分类过滤条件 =====
        // 获取面试记录以确定岗位
        InterviewRecord record = interviewRecordMapper.selectById(recordId);
        String position = record != null ? record.getPosition() : "common";

        Filter categoryFilter;
        if (position.contains("Java")) {
            // Java 岗位：检索 java 分类 + common 通用分类
            categoryFilter = metadataKey("category").isEqualTo("java")
                    .or(metadataKey("category").isEqualTo("common"));
        } else if (position.contains("前端") || position.contains("Web") || position.contains("Frontend")) {
            // 前端岗位：检索 frontend 分类 + common 通用分类
            categoryFilter = metadataKey("category").isEqualTo("frontend")
                    .or(metadataKey("category").isEqualTo("common"));
        } else {
            // 其他情况默认只检索通用分类
            categoryFilter = metadataKey("category").isEqualTo("common");
        }

        // ===== RAG 第二步：构建带过滤条件的检索器（Retriever） =====
        // 基于 EmbeddingStore 中已经存储的知识库向量，对用户输入进行相似度搜索
        ContentRetriever contentRetriever = EmbeddingStoreContentRetriever.builder()
                .embeddingStore(embeddingStore) // 内存级向量数据库
                .embeddingModel(embeddingModel) // 本地嵌入模型
                .filter(categoryFilter) // 【关键】只检索匹配当前岗位的知识片段
                .maxResults(3) // 最多检索 3 条最相关的知识片段
                .minScore(0.6) // 相似度低于 0.6 的结果丢弃，避免无关噪音
                .build();

        // ===== RAG 第二步：执行相似度检索（Similarity Search） =====
        // 将用户的回答向量化，然后在知识库中找到语义最接近的标准答案/考核要点
        // 安全拦截：如果用户输入毫无意义的几个字符（比如只输了一个"1"或者"啊"），拒绝进行重度检索，防止向量空间产生奇葩的相似匹配导致 AI 幻觉
        List<Content> retrievedContents = new ArrayList<>();
        if (message != null && message.trim().length() > 2) {
            retrievedContents = contentRetriever.retrieve(Query.from(message));
        }

        // 拼接检索到的知识片段
        StringBuilder contextBuilder = new StringBuilder();
        for (Content content : retrievedContents) {
            contextBuilder.append("- ").append(content.textSegment().text()).append("\n");
        }

        // ===== RAG 第三步：增强提示词（Prompt Augmentation） =====
        // 将检索到的知识点「偷偷」织入系统提示中，AI 会「看着标准答案」来点评候选人
        String augmentedMessage = message;
        if (contextBuilder.length() > 0) {
            augmentedMessage = String.format(
                    "下面是我（候选人）的当前回答或发言：\n\"%s\"\n\n" +
                            "【后台系统要求（绝密，请严格遵守）】:\n" +
                            "1. 如果候选人的回答极其简短敷衍、毫无意义或者只是打招呼（例如：1、你好、嗯），请**直接指出**，要求候选人认真作答，或者直接抛出下一个提问！**绝对不可以**假装候选人回答了技术问题并进行表扬！\n" +
                            "2. 以下是从字节跳动题库中为你准备的本题【参考考核知识点】：\n%s\n" +
                            "3. 只有当候选人真正在努力回答技术问题时，你才需要对照上述【参考考核知识点】对他的回答进行专业点评，或者顺着知识点深挖追问细节。严禁在对话中暴露你有参考资料。",
                    message, contextBuilder.toString());
        }

        // 将增强后的消息加入聊天记忆，发送给大模型处理
        chatMemory.add(new UserMessage(augmentedMessage));

        // ===== SSE 流式输出：逐 Token 发送给前端，实现打字机效果 =====
        streamingChatModel.generate(chatMemory.messages(), new StreamingResponseHandler<AiMessage>() {
            private StringBuilder fullResponse = new StringBuilder();

            @Override
            public void onNext(String token) {
                try {
                    fullResponse.append(token);
                    // Explicitly construct a JSON string to bypass mapping issues
                    Map<String, String> dataMap = new HashMap<>();
                    dataMap.put("content", token);
                    emitter.send(JSON.toJSONString(dataMap));
                } catch (IOException e) {
                    emitter.completeWithError(e);
                }
            }

            @Override
            public void onComplete(Response<AiMessage> response) {
                try {
                    // 重要：历史记录中只保存用户的「原始回答」，不保存包含 RAG 知识的增强提示词
                    // 否则聊天记忆会被大量检索内容污染，导致后续对话质量下降
                    List<ChatMessage> messages = chatMemory.messages();
                    if (!messages.isEmpty() && messages.get(messages.size() - 1) instanceof UserMessage) {
                        chatMemory.clear();
                        for (int i = 0; i < messages.size() - 1; i++) {
                            chatMemory.add(messages.get(i));
                        }
                        chatMemory.add(new UserMessage(message)); // 替换回用户的纯净原始回答
                    }

                    // 将 AI 的完整回复保存到聊天记忆中
                    chatMemory.add(response.content());

                    // Signal the end of stream
                    Map<String, String> doneMap = new HashMap<>();
                    doneMap.put("done", "true");
                    emitter.send(JSON.toJSONString(doneMap));
                    emitter.complete();
                } catch (IOException e) {
                    emitter.completeWithError(e);
                }
            }

            @Override
            public void onError(Throwable error) {
                try {
                    String userMsg = error.getMessage();
                    // 增加对国内网络环境的友好提示 (SSL 握手失败通常是代理或防火墙问题)
                    if (error instanceof javax.net.ssl.SSLHandshakeException || 
                        (error.getCause() != null && error.getCause().getMessage() != null && error.getCause().getMessage().contains("SSL"))) {
                        userMsg = "网络连接失败 (SSL Handshake Error)。请检查你的网络代理设置，或尝试更换 API 地址。";
                    }
                    
                    Map<String, String> errMap = new HashMap<>();
                    errMap.put("error", userMsg);
                    emitter.send(JSON.toJSONString(errMap));
                    
                    // 使用 complete() 而不是 completeWithError() 
                    // 避免 Spring Boot 的全局异常处理器在 SSE 已提交的情况下二次报错 HttpMessageNotWritableException
                    emitter.complete();
                } catch (Exception e) {
                    emitter.complete();
                }
            }
        });

        return emitter;
    }

    /**
     * 结束面试（默认 wpm=0）
     */
    @Override
    public InterviewRecord endInterview(Long recordId) {
        return endInterview(recordId, 0);
    }

    /**
     * 结束面试（带语速数据）
     * 调用 AI 生成结构化评估报告：包含综合分、文字反馈、六维能力评级、提升建议
     */
    @Override
    public InterviewRecord endInterview(Long recordId, Integer wpm) {
        ChatMemory chatMemory = chatMemories.remove(recordId);
        if (chatMemory == null) {
            return interviewRecordMapper.selectById(recordId);
        }

        InterviewRecord record = interviewRecordMapper.selectById(recordId);
        if (record != null) {
            record.setEndTime(LocalDateTime.now());
            record.setChatHistory(JSON.toJSONString(chatMemory.messages()));
            record.setVoiceWpm(wpm != null ? wpm : 0);

            try {
                // ============================================================
                // Phase 6 增强版 Prompt：要求返回完整结构化 JSON
                // ============================================================
                String evaluationPrompt = String.format("""
                        本场面试已结束。请根据上面所有对话内容进行全面评估。候选人本次面试的平均语速为 %d WPM。

                        请严格只返回一个符合以下格式的纯 JSON 对象，不要包含任何 Markdown 代码块标记（不要有```json）或解释文字：
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
                            {"period": "本周", "action": "刷题方向", "detail": "建议重点复习..."},
                            {"period": "两周内", "action": "项目实践", "detail": "尝试实现..."},
                            {"period": "一个月", "action": "系统提升", "detail": "可系统学习..."}
                          ]
                        }

                        【评分说明】
                        - score: 0-100 综合得分
                        - feedback: 综合点评，需包含技术能力、表达自信度、逻辑性三个角度
                        - ability 六维能力评级（S/A/B/C/D）：
                            - techDepth: 技术深度（核心知识点掌握程度）
                            - breadth: 知识广度（跨领域知识覆盖）
                            - problemSolving: 解题思路（分析和解决问题的能力）
                            - expression: 表达清晰度（语言组织与沟通效果）
                            - logic: 逻辑思维（回答的结构性与条理）
                            - adaptability: 应变能力（对追问和新问题的反应）
                        - recommendations: 3条具体且可执行的提升建议，period 可以是 "本周"/"两周内"/"一个月"
                        """, wpm);

                chatMemory.add(new SystemMessage(evaluationPrompt));
                Response<AiMessage> evalResponse = chatModel.generate(chatMemory.messages());
                String raw = evalResponse.content().text().replace("```json", "").replace("```", "").trim();

                com.alibaba.fastjson2.JSONObject evalObj = JSON.parseObject(raw);
                record.setScore(evalObj.getInteger("score") != null ? evalObj.getInteger("score") : 0);
                record.setFeedback(evalObj.getString("feedback") != null ? evalObj.getString("feedback") : "");

                // 存储六维能力 JSON 字符串
                com.alibaba.fastjson2.JSONObject abilityObj = evalObj.getJSONObject("ability");
                if (abilityObj != null)
                    record.setAbilityJson(abilityObj.toJSONString());

                // 存储建议列表 JSON 字符串
                com.alibaba.fastjson2.JSONArray recArr = evalObj.getJSONArray("recommendations");
                if (recArr != null)
                    record.setRecommendations(recArr.toJSONString());

            } catch (Exception e) {
                e.printStackTrace();
                record.setFeedback("AI 评估生成异常: " + e.getMessage());
                record.setScore(0);
            }

            interviewRecordMapper.updateById(record);
        }
        return record;
    }
}
