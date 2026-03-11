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
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class InterviewServiceImpl implements InterviewService {

    @Autowired
    private InterviewRecordMapper interviewRecordMapper;

    @Autowired
    private OpenAiStreamingChatModel streamingChatModel;
    
    @Autowired
    private OpenAiChatModel chatModel;

    // In-memory store for active chat sessions
    private final Map<Long, ChatMemory> chatMemories = new ConcurrentHashMap<>();

    @Override
    public Long startInterview(Long userId, String position) {
        InterviewRecord record = new InterviewRecord();
        record.setUserId(userId);
        record.setPosition(position);
        record.setCreateTime(LocalDateTime.now());
        record.setChatHistory("[]"); // Initialize empty JSON array
        
        interviewRecordMapper.insert(record);
        
        // Initialize memory for this session
        ChatMemory chatMemory = MessageWindowChatMemory.withMaxMessages(20);
        
        // Add System Prompt
        String systemPrompt = String.format(
            "你是一个专业的面试官，正在面试候选人【%s】岗位。请注意：\n" +
            "1. 你的语气要专业、严肃但有礼貌。\n" +
            "2. 每次只问一个问题。刚开始请让候选人做个简短的自我介绍。\n" +
            "3. 根据候选人的回答深入追问技术细节或八股文知识。\n" +
            "4. 尽量口语化，像真实的面试对话，不要长篇大论。", position);
            
        chatMemory.add(new SystemMessage(systemPrompt));
        chatMemories.put(record.getId(), chatMemory);
        
        return record.getId();
    }

    @Override
    public SseEmitter chatStream(Long userId, Long recordId, String message) {
        SseEmitter emitter = new SseEmitter(0L); // 0 means no timeout

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

        // Add user message to memory
        chatMemory.add(new UserMessage(message));

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
                    // Save response to memory
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
                    Map<String, String> errMap = new HashMap<>();
                    errMap.put("error", error.getMessage());
                    emitter.send(JSON.toJSONString(errMap));
                    emitter.completeWithError(error);
                } catch (IOException e) {
                    // Ignore
                }
            }
        });

        return emitter;
    }

    @Override
    public InterviewRecord endInterview(Long recordId) {
        ChatMemory chatMemory = chatMemories.remove(recordId);
        if (chatMemory == null) {
            return interviewRecordMapper.selectById(recordId);
        }

        InterviewRecord record = interviewRecordMapper.selectById(recordId);
        if (record != null) {
            record.setEndTime(LocalDateTime.now());
            // serialize messages to JSON and save
            String jsonHistory = JSON.toJSONString(chatMemory.messages());
            record.setChatHistory(jsonHistory);
            
            try {
                // Generate Evaluation Report
                chatMemory.add(new SystemMessage("面试结束，请根据本次面试的所有对话，对候选人进行评价。请务必只返回一个纯JSON对象，格式如下：{\"score\": 85, \"feedback\": \"候选人基础扎实，但在XX方面有待提高...\"}。不要包含任何其他说明文字或markdown代码块标记。"));
                Response<AiMessage> evalResponse = chatModel.generate(chatMemory.messages());
                String evalJsonStr = evalResponse.content().text();
                
                // Extremely simple cleanup in case model returns markdown block
                evalJsonStr = evalJsonStr.replace("```json", "").replace("```", "").trim();
                
                com.alibaba.fastjson2.JSONObject evalObj = JSON.parseObject(evalJsonStr);
                Integer score = evalObj.getInteger("score");
                String feedback = evalObj.getString("feedback");
                
                record.setScore(score != null ? score : 0);
                record.setFeedback(feedback != null ? feedback : "AI评估失败或格式不正确");
            } catch (Exception e) {
                e.printStackTrace();
                record.setFeedback("生成评价报告异常：" + e.getMessage());
                record.setScore(0);
            }

            interviewRecordMapper.updateById(record);
        }
        return record;
    }
}
