package com.interview.service;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.interview.dto.MentorInsightResponse;
import com.interview.dto.MentorInsightResponse.*;
import com.interview.entity.InterviewRecord;
import com.interview.mapper.InterviewRecordMapper;
import com.interview.mapper.RagRetrievalLogMapper;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.output.Response;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
@Slf4j
public class MentorService {

    @Autowired
    private InterviewRecordMapper recordMapper;

    @Autowired
    private RagRetrievalLogMapper ragLogMapper;

    @Autowired
    private ChatLanguageModel chatModel;

    @Autowired(required = false)
    private RedisTemplate<String, Object> redisTemplate;

    /** 内存缓存回退（Redis 不可用时使用，TTL 12小时） */
    private final Map<Long, MentorInsightResponse> localCache = new java.util.concurrent.ConcurrentHashMap<>();
    private final Map<Long, Long> localCacheExpiry = new java.util.concurrent.ConcurrentHashMap<>();
    private static final long LOCAL_TTL_MS = 12 * 60 * 60 * 1000; // 12 hours

    private static final String CACHE_KEY_PREFIX = "mentor:insight:";
    private static final long CACHE_TTL_HOURS = 24;

    /**
     * 获取 AI Mentor 洞察报告（含缓存）。
     */
    public MentorInsightResponse getInsight(Long userId) {
        return getInsight(userId, false);
    }

    /**
     * 获取 AI Mentor 洞察报告，可按需绕过缓存并重新生成。
     */
    public MentorInsightResponse getInsight(Long userId, boolean forceRefresh) {
        if (forceRefresh) {
            evictCache(userId);
        }
        // 尝试缓存命中
        MentorInsightResponse cached = getCached(userId);
        if (cached != null) return cached;

        // 构建报告
        MentorInsightResponse report = new MentorInsightResponse();

        // 1. 知识领域覆盖（无需 LLM）
        report.setKnowledgeCoverage(buildKnowledgeCoverage(userId));

        // 2. 聚合面试历史
        List<InterviewRecord> history = getHistory(userId);
        if (history.isEmpty()) {
            report.setDiagnosis(emptyDiagnosis());
            report.setRiskAlerts(Collections.emptyList());
            report.setActions(Collections.emptyList());
        } else {
            // 3. AI 分析
            String aiOutput = callMentorLLM(userId, history, report.getKnowledgeCoverage());
            parseAiOutput(aiOutput, report);
        }

        report.setGeneratedAt(LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));

        // 写入缓存
        saveCache(userId, report);
        return report;
    }

    /**
     * 仅获取知识覆盖数据（快速，无 LLM 调用）。
     */
    public MentorInsightResponse getKnowledgeCoverageOnly(Long userId) {
        MentorInsightResponse report = new MentorInsightResponse();
        report.setKnowledgeCoverage(buildKnowledgeCoverage(userId));
        report.setGeneratedAt(LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        return report;
    }

    private KnowledgeCoverage buildKnowledgeCoverage(Long userId) {
        KnowledgeCoverage kc = new KnowledgeCoverage();

        // 查询该用户所有检索日志中的分类统计（去重 atom_id）
        com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<com.interview.entity.RagRetrievalLog> query =
                new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<>();
        query.select("retrieved_category, COUNT(DISTINCT retrieved_atom_id) as cnt")
             .eq("user_id", userId)
             .groupBy("retrieved_category");
        List<Map<String, Object>> rows = ragLogMapper.selectMaps(query);

        int coveredCats = 0;
        int coveredTotal = 0;
        List<KnowledgeCoverage.CategoryDetail> details = new ArrayList<>();

        for (Map<String, Object> row : rows) {
            String cat = (String) row.get("retrieved_category");
            Number cnt = (Number) row.get("cnt");
            if (cat == null || cnt == null) continue;

            KnowledgeCoverage.CategoryDetail detail = new KnowledgeCoverage.CategoryDetail();
            detail.setCategory(cat);
            detail.setCovered(cnt.intValue());
            detail.setTotal(cnt.intValue()); // total from same source for now
            detail.setPercent(100.0);
            details.add(detail);
            coveredCats++;
            coveredTotal += cnt.intValue();
        }

        kc.setTotalCategories(coveredCats); // only categories that have been hit
        kc.setCoveredCategories(coveredCats);
        kc.setCoveragePercent(coveredCats > 0 ? 100.0 : 0.0);
        kc.setDetails(details);
        return kc;
    }

    private List<InterviewRecord> getHistory(Long userId) {
        com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<InterviewRecord> query =
                new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<>();
        query.eq("user_id", userId)
             .isNotNull("score")
             .orderByDesc("create_time")
             .last("LIMIT 10");
        return recordMapper.selectList(query);
    }

    private String callMentorLLM(Long userId, List<InterviewRecord> history, KnowledgeCoverage coverage) {
        // 构造简洁的面试历史摘要
        StringBuilder historySummary = new StringBuilder();
        for (int i = 0; i < history.size(); i++) {
            InterviewRecord r = history.get(i);
            historySummary.append(String.format("%d. %s | 得分:%d | %s | %s\n",
                    i + 1, r.getPosition(), r.getScore() != null ? r.getScore() : 0,
                    r.getInterviewMode(), r.getCreateTime() != null ? r.getCreateTime().toString() : ""));
            String ability = r.getAbilityJson();
            if (ability != null && !ability.isEmpty()) {
                historySummary.append("   能力评级: ").append(ability).append("\n");
            }
            String emotion = r.getEmotionJson();
            if (emotion != null && !emotion.isEmpty()) {
                try {
                    JSONObject em = JSON.parseObject(emotion);
                    historySummary.append(String.format("   自信度:%.0f%% 主导情绪:%s\n",
                            em.getDoubleValue("avgConfidence") * 100,
                            em.getString("dominantEmotion")));
                } catch (Exception ignored) {}
            }
        }

        String systemPrompt = """
                你是用户的专属 AI 面试教练（Mentor）。请根据用户的面试历史数据和知识覆盖情况，
                生成一份个性化分析报告。

                输出必须是严格的 JSON 格式，结构如下：
                {
                  "diagnosis": {
                    "overview": "1-2句对用户当前面试表现的整体评价",
                    "strengths": ["2-3个优势点"],
                    "weaknesses": ["2-3个需要提升的点"]
                  },
                  "riskAlerts": [
                    {"type": "score_drop|emotion|coverage", "message": "具体描述", "severity": "info|warning|danger"}
                  ],
                  "actions": [
                    {"category": "领域名", "message": "具体可执行的下一步行动", "priority": 1}
                  ]
                }

                要求：
                - 建议要具体可执行，不要泛泛而谈
                - 风险预警要有数据支撑
                - 行动项按优先级排列（1=立即，2=短期，3=长期）
                - 只输出 JSON，不要其他内容
                """;

        List<dev.langchain4j.data.message.ChatMessage> messages = List.of(
                new SystemMessage(systemPrompt),
                new UserMessage(historySummary.toString())
        );
        Response<AiMessage> response = chatModel.generate(messages);
        return response.content().text()
                .replace("```json", "").replace("```", "").trim();
    }

    private void parseAiOutput(String raw, MentorInsightResponse report) {
        try {
            JSONObject obj = JSON.parseObject(raw);

            // Diagnosis
            JSONObject diagObj = obj.getJSONObject("diagnosis");
            if (diagObj != null) {
                Diagnosis diag = new Diagnosis();
                diag.setOverview(diagObj.getString("overview"));
                diag.setStrengths(toStrList(diagObj.getJSONArray("strengths")));
                diag.setWeaknesses(toStrList(diagObj.getJSONArray("weaknesses")));
                report.setDiagnosis(diag);
            }

            // Risk alerts
            com.alibaba.fastjson2.JSONArray riskArr = obj.getJSONArray("riskAlerts");
            if (riskArr != null) {
                List<RiskAlert> risks = new ArrayList<>();
                for (int i = 0; i < riskArr.size(); i++) {
                    JSONObject r = riskArr.getJSONObject(i);
                    RiskAlert alert = new RiskAlert();
                    alert.setType(r.getString("type"));
                    alert.setMessage(r.getString("message"));
                    alert.setSeverity(r.getString("severity"));
                    risks.add(alert);
                }
                report.setRiskAlerts(risks);
            }

            // Actions
            com.alibaba.fastjson2.JSONArray actArr = obj.getJSONArray("actions");
            if (actArr != null) {
                List<ActionItem> actions = new ArrayList<>();
                for (int i = 0; i < actArr.size(); i++) {
                    JSONObject a = actArr.getJSONObject(i);
                    ActionItem item = new ActionItem();
                    item.setCategory(a.getString("category"));
                    item.setMessage(a.getString("message"));
                    item.setPriority(a.getInteger("priority"));
                    actions.add(item);
                }
                report.setActions(actions);
            }

        } catch (Exception e) {
            log.warn("AI Mentor 解析失败: {}", e.getMessage());
            report.setDiagnosis(emptyDiagnosis());
            report.setRiskAlerts(Collections.emptyList());
            report.setActions(Collections.emptyList());
        }
    }

    private List<String> toStrList(com.alibaba.fastjson2.JSONArray arr) {
        if (arr == null) return Collections.emptyList();
        List<String> list = new ArrayList<>();
        for (int i = 0; i < arr.size(); i++) {
            list.add(arr.getString(i));
        }
        return list;
    }

    private Diagnosis emptyDiagnosis() {
        Diagnosis d = new Diagnosis();
        d.setOverview("暂无面试数据，AI Mentor 将在你完成首次面试后生成分析报告。");
        d.setStrengths(Collections.emptyList());
        d.setWeaknesses(Collections.emptyList());
        return d;
    }

    @SuppressWarnings("unchecked")
    private MentorInsightResponse getCached(Long userId) {
        // Redis 优先
        if (redisTemplate != null) {
            try {
                Object raw = redisTemplate.opsForValue().get(CACHE_KEY_PREFIX + userId);
                if (raw instanceof String s) {
                    return JSON.parseObject(s, MentorInsightResponse.class);
                }
            } catch (Exception e) {
                log.trace("Redis 缓存读取跳过: {}", e.getMessage());
            }
        }
        // 内存缓存回退
        Long expiry = localCacheExpiry.get(userId);
        if (expiry != null && System.currentTimeMillis() < expiry) {
            return localCache.get(userId);
        }
        if (expiry != null) {
            localCache.remove(userId);
            localCacheExpiry.remove(userId);
        }
        return null;
    }

    private void saveCache(Long userId, MentorInsightResponse report) {
        if (redisTemplate != null) {
            try {
                redisTemplate.opsForValue().set(CACHE_KEY_PREFIX + userId,
                        JSON.toJSONString(report), CACHE_TTL_HOURS, TimeUnit.HOURS);
            } catch (Exception e) {
                log.trace("Redis 缓存写入跳过: {}", e.getMessage());
            }
        }
        localCache.put(userId, report);
        localCacheExpiry.put(userId, System.currentTimeMillis() + LOCAL_TTL_MS);
    }

    private void evictCache(Long userId) {
        if (redisTemplate != null) {
            try {
                redisTemplate.delete(CACHE_KEY_PREFIX + userId);
            } catch (Exception e) {
                log.trace("Redis 缓存删除跳过: {}", e.getMessage());
            }
        }
        localCache.remove(userId);
        localCacheExpiry.remove(userId);
    }
}
