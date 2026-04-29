package com.interview.service;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.interview.config.InterviewPrompts;
import com.interview.entity.InterviewRecord;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.output.Response;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;

/**
 * AI 评估报告生成器：封装提示词构建、LLM 调用、JSON 解析、字段映射。
 */
@Slf4j
public class EvaluationGenerator {

    private final ChatLanguageModel chatModel;
    private final InterviewPrompts prompts;

    public EvaluationGenerator(ChatLanguageModel chatModel, InterviewPrompts prompts) {
        this.chatModel = chatModel;
        this.prompts = prompts;
    }

    /**
     * 根据对话历史生成评估并填充到 InterviewRecord 中。
     */
    public void generate(InterviewRecord record, List<ChatMessage> historyMessages, int wpm) {
        String evaluationPrompt = String.format(prompts.getEvaluation(), wpm);

        List<ChatMessage> evalMessages = new ArrayList<>();
        evalMessages.add(new SystemMessage(evaluationPrompt));
        for (ChatMessage msg : historyMessages) {
            if (!(msg instanceof SystemMessage)) evalMessages.add(msg);
        }
        evalMessages.add(new UserMessage("面试已结束。请立即输出 JSON 格式的评估报告，不要输出任何其他内容。"));

        try {
            log.info("开始生成 AI 评估报告 (recordId={}, 对话轮数={})", record.getId(), historyMessages.size());
            Response<AiMessage> evalResponse = chatModel.generate(evalMessages);
            String raw = evalResponse.content().text().replace("```json", "").replace("```", "").trim();
            log.info("AI 评估原始响应: {}", raw);

            JSONObject evalObj = JSON.parseObject(raw);
            record.setScore(evalObj.getInteger("score") != null ? evalObj.getInteger("score") : 0);
            record.setFeedback(evalObj.getString("feedback") != null ? evalObj.getString("feedback") : "评估内容生成异常");
            if (evalObj.get("ability") != null)
                record.setAbilityJson(evalObj.getJSONObject("ability").toJSONString());
            if (evalObj.get("recommendations") != null)
                record.setRecommendations(evalObj.getJSONArray("recommendations").toJSONString());
            if (evalObj.get("knowledgePoints") != null)
                record.setKnowledgeJson(evalObj.getJSONArray("knowledgePoints").toJSONString());

            // 文本情感分析
            if ((record.getEmotionJson() == null || record.getEmotionJson().isEmpty())
                    && evalObj.get("sentimentAnalysis") != null) {
                JSONObject sentiment = evalObj.getJSONObject("sentimentAnalysis");
                sentiment.put("source", "text");
                record.setEmotionJson(sentiment.toJSONString());
            } else if (record.getEmotionJson() != null && !record.getEmotionJson().isEmpty()
                    && evalObj.get("sentimentAnalysis") != null) {
                try {
                    JSONObject existing = JSON.parseObject(record.getEmotionJson());
                    existing.put("source", "video");
                    String summary = evalObj.getJSONObject("sentimentAnalysis").getString("summary");
                    if (summary != null) existing.put("summary", summary);
                    record.setEmotionJson(existing.toJSONString());
                } catch (Exception ignored) {}
            }
        } catch (Exception e) {
            log.error("评估生成失败 (recordId={})", record.getId(), e);
            record.setScore(0);
            record.setFeedback("AI 评估生成异常: " + e.getMessage());
        }
    }
}
