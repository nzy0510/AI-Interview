package com.interview.service;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.interview.entity.AppJob;
import com.interview.entity.InterviewPhase;
import com.interview.entity.InterviewRecord;
import com.interview.entity.InterviewReport;
import com.interview.entity.InterviewReportItem;
import com.interview.entity.InterviewTurn;
import com.interview.mapper.InterviewRecordMapper;
import com.interview.mapper.InterviewReportItemMapper;
import com.interview.mapper.InterviewReportMapper;
import com.interview.mapper.InterviewTurnMapper;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.output.Response;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

@Service
public class GenerateReportJobHandler implements AppJobHandler {

    public static final String JOB_TYPE = "GENERATE_REPORT";
    private static final Pattern SENSITIVE_PATTERN = Pattern.compile(
            "(?i)(Authorization\\s*[:=]\\s*\\S+|Bearer\\s+\\S+|api_key\\s*[:=]\\s*\\S+|sk-[A-Za-z0-9_-]+)"
    );

    private final AppJobService appJobService;
    private final InterviewRecordMapper recordMapper;
    private final InterviewReportMapper reportMapper;
    private final InterviewReportItemMapper reportItemMapper;
    private final InterviewTurnMapper turnMapper;
    private final UserLlmConfigService userLlmConfigService;
    private final UserLlmModelFactory userLlmModelFactory;

    public GenerateReportJobHandler(AppJobService appJobService,
                                    InterviewRecordMapper recordMapper,
                                    InterviewReportMapper reportMapper,
                                    InterviewReportItemMapper reportItemMapper,
                                    InterviewTurnMapper turnMapper,
                                    UserLlmConfigService userLlmConfigService,
                                    UserLlmModelFactory userLlmModelFactory) {
        this.appJobService = appJobService;
        this.recordMapper = recordMapper;
        this.reportMapper = reportMapper;
        this.reportItemMapper = reportItemMapper;
        this.turnMapper = turnMapper;
        this.userLlmConfigService = userLlmConfigService;
        this.userLlmModelFactory = userLlmModelFactory;
    }

    @Override
    public String jobType() {
        return JOB_TYPE;
    }

    @Override
    public void handle(AppJob job) {
        if (job.getRecordId() == null) {
            throw new IllegalArgumentException("报告生成作业缺少面试记录");
        }
        InterviewRecord record = recordMapper.selectById(job.getRecordId());
        if (record == null) {
            throw new IllegalArgumentException("面试记录不存在");
        }
        InterviewReport report = ensureReport(record, job);
        markReport(report, "RUNNING", null);
        try {
            appJobService.updateRunningJob(job.getId(), job.getClaimedBy(), "GENERATING_REPORT", 30);

            if (record.getScore() == null || record.getFeedback() == null || record.getFeedback().isBlank()) {
                throw new IllegalStateException("初步报告尚未生成，无法生成详细报告");
            }

            List<InterviewTurn> turns = loadReportTurns(record);
            DetailedReportResult detail = generateDetailedReport(record, turns);

            report.setOverallScore(detail.overallScore());
            report.setSummary(nonBlank(detail.summary(), record.getFeedback()));
            report.setAbilityJson(record.getAbilityJson());
            report.setRecommendationJson(record.getRecommendations());
            report.setModelProvider(detail.runtimeConfig().provider());
            report.setModelName(detail.runtimeConfig().modelName());
            report.setStatus("COMPLETED");
            report.setErrorMessage(null);
            report.setGeneratedAt(LocalDateTime.now());
            reportMapper.updateById(report);

            appJobService.updateRunningJob(job.getId(), job.getClaimedBy(), "WRITING_REPORT_ITEMS", 80);
            rewriteReportItems(report, record, turns, detail);
            job.setResultJson(JSON.toJSONString(java.util.Map.of(
                    "recordId", record.getId(),
                    "reportId", report.getId(),
                    "status", "COMPLETED"
            )));
        } catch (RuntimeException e) {
            markReport(report, "FAILED", sanitize(e.getMessage()));
            throw e;
        }
    }

    private InterviewReport ensureReport(InterviewRecord record, AppJob job) {
        InterviewReport report = reportMapper.selectOne(new QueryWrapper<InterviewReport>()
                .eq("record_id", record.getId())
                .last("LIMIT 1"));
        if (report == null) {
            report = new InterviewReport();
            report.setRecordId(record.getId());
            report.setUserId(record.getUserId());
            report.setPositionId(record.getPositionId());
            report.setJobId(job.getId());
            report.setStatus("PENDING");
            reportMapper.insert(report);
        } else {
            report.setJobId(job.getId());
            reportMapper.updateById(report);
        }
        return report;
    }

    private void markReport(InterviewReport report, String status, String errorMessage) {
        report.setStatus(status);
        report.setErrorMessage(errorMessage);
        reportMapper.updateById(report);
    }

    private String sanitize(String message) {
        if (message == null || message.isBlank()) {
            return "报告生成失败";
        }
        String sanitized = SENSITIVE_PATTERN.matcher(message).replaceAll("[REDACTED]");
        return sanitized.length() > 500 ? sanitized.substring(0, 500) : sanitized;
    }

    private List<InterviewTurn> loadReportTurns(InterviewRecord record) {
        return turnMapper.selectList(new QueryWrapper<InterviewTurn>()
                .eq("record_id", record.getId())
                .in("phase", List.of(InterviewPhase.TECHNICAL.name(), InterviewPhase.HR.name()))
                .orderByAsc("turn_index"));
    }

    private DetailedReportResult generateDetailedReport(InterviewRecord record, List<InterviewTurn> turns) {
        if (turns == null || turns.isEmpty()) {
            throw new IllegalStateException("缺少逐轮问答，无法生成详细报告");
        }
        UserLlmRuntimeConfig runtimeConfig = userLlmConfigService.requireActiveRuntimeConfig(record.getUserId());
        OpenAiChatModel model = userLlmModelFactory.createChatModel(runtimeConfig);
        Response<AiMessage> response = model.generate(List.of(
                new SystemMessage(detailedReportSystemPrompt()),
                new UserMessage(buildDetailedReportUserPrompt(record, turns))
        ));
        String raw = response.content().text();
        JSONObject parsed = parseJsonObject(raw);
        Map<Long, DetailedReportItemResult> itemsByTurnId = parseDetailedItems(parsed.getJSONArray("items"));
        if (itemsByTurnId.isEmpty()) {
            throw new IllegalStateException("详细报告未返回逐题评分");
        }
        validateDetailedItemsComplete(turns, itemsByTurnId);
        int overallScore = calculateOverallScore(turns, itemsByTurnId);
        String summary = parsed.getString("summary");
        return new DetailedReportResult(overallScore, summary, itemsByTurnId, runtimeConfig);
    }

    private void validateDetailedItemsComplete(List<InterviewTurn> turns,
                                               Map<Long, DetailedReportItemResult> itemsByTurnId) {
        for (InterviewTurn turn : turns) {
            if (!itemsByTurnId.containsKey(turn.getId())) {
                throw new IllegalStateException("详细报告缺少第 " + turn.getTurnIndex() + " 轮评分");
            }
        }
    }

    private void rewriteReportItems(InterviewReport report,
                                    InterviewRecord record,
                                    List<InterviewTurn> turns,
                                    DetailedReportResult detail) {
        reportItemMapper.delete(new QueryWrapper<InterviewReportItem>().eq("report_id", report.getId()));
        int index = 1;
        for (InterviewTurn turn : turns) {
            DetailedReportItemResult detailItem = detail.itemsByTurnId().get(turn.getId());
            if (detailItem == null) {
                throw new IllegalStateException("详细报告缺少第 " + turn.getTurnIndex() + " 轮评分");
            }
            InterviewReportItem item = new InterviewReportItem();
            item.setReportId(report.getId());
            item.setRecordId(record.getId());
            item.setTurnId(turn.getId());
            item.setItemIndex(index++);
            item.setPhase(turn.getPhase());
            item.setQuestion(turn.getAiQuestion());
            item.setUserAnswer(turn.getUserAnswer());
            item.setScore(detailItem.score());
            item.setReferenceAnswer(detailItem.referenceAnswer());
            item.setImprovementSuggestion(formatImprovementSuggestion(detailItem));
            item.setAnswerSource(turn.getRetrievedAtomIds() == null ? "AI_GENERATED" : "KNOWLEDGE_BASE");
            item.setMatchedAtomSnapshotJson(turn.getContextSnapshotJson());
            item.setModelProvider(detail.runtimeConfig().provider());
            item.setModelName(detail.runtimeConfig().modelName());
            item.setGeneratedTime(LocalDateTime.now());
            reportItemMapper.insert(item);
        }
    }

    private String detailedReportSystemPrompt() {
        return """
                你是严谨的技术面试评估官。你要基于每轮问题、候选人回答和知识库命中内容，生成历史面试详细报告。
                必须输出 JSON，不要输出 Markdown 或解释文本。
                每题 10 分，评分细则固定为：相关性 2 分、正确性 3 分、深度 2 分、实践性 2 分、表达 1 分。
                参考答案必须综合知识库证据形成可直接学习的标准答案，不要堆砌、复制或罗列原子全文。
                如果知识库证据不足，可以结合通用专业知识补足，但必须保持简洁、可操作。
                """;
    }

    private String buildDetailedReportUserPrompt(InterviewRecord record, List<InterviewTurn> turns) {
        StringBuilder builder = new StringBuilder();
        builder.append("初步总分：").append(record.getScore()).append("/100\n");
        builder.append("初步反馈：").append(record.getFeedback()).append("\n\n");
        builder.append("请按如下 JSON 结构返回：\n");
        builder.append("""
                {
                  "summary": "详细报告总评",
                  "items": [
                    {
                      "turnId": 123,
                      "score": 7.5,
                      "referenceAnswer": "综合参考答案",
                      "scoreBreakdown": {
                        "relevance": 2,
                        "correctness": 3,
                        "depth": 1.5,
                        "practicality": 1,
                        "communication": 1
                      },
                      "improvementSuggestion": "针对本题的改进建议"
                    }
                  ]
                }
                """);
        builder.append("\n逐轮材料：\n");
        for (InterviewTurn turn : turns) {
            builder.append("turnId: ").append(turn.getId()).append("\n");
            builder.append("轮次: ").append(turn.getTurnIndex()).append("\n");
            builder.append("阶段: ").append(turn.getPhase()).append("\n");
            builder.append("问题: ").append(nonBlank(turn.getAiQuestion(), "未记录")).append("\n");
            builder.append("候选人回答: ").append(nonBlank(turn.getUserAnswer(), "未作答")).append("\n");
            builder.append("知识库命中内容:\n").append(extractReferenceAnswer(turn.getContextSnapshotJson())).append("\n\n");
        }
        return builder.toString();
    }

    private JSONObject parseJsonObject(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalStateException("详细报告模型返回为空");
        }
        String cleaned = raw.replace("```json", "").replace("```", "").trim();
        return JSON.parseObject(cleaned);
    }

    private Map<Long, DetailedReportItemResult> parseDetailedItems(JSONArray rawItems) {
        Map<Long, DetailedReportItemResult> results = new HashMap<>();
        if (rawItems == null) {
            return results;
        }
        for (Object rawItem : rawItems) {
            if (!(rawItem instanceof JSONObject item)) {
                continue;
            }
            Long turnId = item.getLong("turnId");
            if (turnId == null) {
                continue;
            }
            BigDecimal score = normalizeScore(item.getBigDecimal("score"));
            String referenceAnswer = item.getString("referenceAnswer");
            if (referenceAnswer == null || referenceAnswer.isBlank()) {
                throw new IllegalStateException("详细报告缺少综合参考答案");
            }
            JSONObject breakdown = item.getJSONObject("scoreBreakdown");
            ScoreBreakdown scoreBreakdown = new ScoreBreakdown(
                    normalizeScorePart(breakdown, "relevance", "2"),
                    normalizeScorePart(breakdown, "correctness", "3"),
                    normalizeScorePart(breakdown, "depth", "2"),
                    normalizeScorePart(breakdown, "practicality", "2"),
                    normalizeScorePart(breakdown, "communication", "1")
            );
            results.put(turnId, new DetailedReportItemResult(
                    score,
                    referenceAnswer.trim(),
                    scoreBreakdown,
                    item.getString("improvementSuggestion")
            ));
        }
        return results;
    }

    private BigDecimal normalizeScore(BigDecimal score) {
        if (score == null) {
            throw new IllegalStateException("详细报告缺少逐题分数");
        }
        BigDecimal bounded = score.max(BigDecimal.ZERO).min(BigDecimal.TEN);
        return bounded.setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal normalizeScorePart(JSONObject breakdown, String key, String maxScore) {
        BigDecimal max = new BigDecimal(maxScore);
        if (breakdown == null) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        BigDecimal value = breakdown.getBigDecimal(key);
        if (value == null) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        return value.max(BigDecimal.ZERO).min(max).setScale(2, RoundingMode.HALF_UP);
    }

    private int calculateOverallScore(List<InterviewTurn> turns, Map<Long, DetailedReportItemResult> itemsByTurnId) {
        BigDecimal total = BigDecimal.ZERO;
        int matched = 0;
        for (InterviewTurn turn : turns) {
            DetailedReportItemResult item = itemsByTurnId.get(turn.getId());
            if (item != null) {
                total = total.add(item.score());
                matched++;
            }
        }
        if (matched == 0) {
            throw new IllegalStateException("详细报告没有可汇总的逐题评分");
        }
        return total
                .divide(BigDecimal.valueOf(matched), 2, RoundingMode.HALF_UP)
                .multiply(BigDecimal.TEN)
                .setScale(0, RoundingMode.HALF_UP)
                .intValue();
    }

    private String formatImprovementSuggestion(DetailedReportItemResult item) {
        ScoreBreakdown breakdown = item.scoreBreakdown();
        List<String> parts = new ArrayList<>();
        parts.add("评分依据：相关性 " + formatDecimal(breakdown.relevance()) + "/2");
        parts.add("正确性 " + formatDecimal(breakdown.correctness()) + "/3");
        parts.add("深度 " + formatDecimal(breakdown.depth()) + "/2");
        parts.add("实践性 " + formatDecimal(breakdown.practicality()) + "/2");
        parts.add("表达 " + formatDecimal(breakdown.communication()) + "/1");
        String suggestion = item.improvementSuggestion();
        if (suggestion != null && !suggestion.isBlank()) {
            parts.add("改进建议：" + suggestion.trim());
        }
        return String.join("；", parts);
    }

    private String formatDecimal(BigDecimal value) {
        if (value == null) {
            return "0";
        }
        return value.stripTrailingZeros().toPlainString();
    }

    private String extractReferenceAnswer(String contextSnapshotJson) {
        if (contextSnapshotJson == null || contextSnapshotJson.isBlank()) {
            return "暂无结构化参考答案";
        }
        try {
            com.alibaba.fastjson2.JSONObject snapshot = JSON.parseObject(contextSnapshotJson);
            String promptContext = snapshot.getString("promptContext");
            if (promptContext != null && !promptContext.isBlank()) {
                return promptContext;
            }
        } catch (Exception ignored) {
        }
        return contextSnapshotJson;
    }

    private String nonBlank(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private record DetailedReportResult(Integer overallScore,
                                        String summary,
                                        Map<Long, DetailedReportItemResult> itemsByTurnId,
                                        UserLlmRuntimeConfig runtimeConfig) {
    }

    private record DetailedReportItemResult(BigDecimal score,
                                            String referenceAnswer,
                                            ScoreBreakdown scoreBreakdown,
                                            String improvementSuggestion) {
    }

    private record ScoreBreakdown(BigDecimal relevance,
                                  BigDecimal correctness,
                                  BigDecimal depth,
                                  BigDecimal practicality,
                                  BigDecimal communication) {
    }
}
