package com.interview.service;

import com.interview.dto.questionbank.QuestionBankSearchRequest;
import com.interview.dto.questionbank.QuestionBankSearchResponse;
import com.interview.dto.questionbank.QuestionBankSearchResult;
import com.interview.entity.InterviewPhase;
import com.interview.entity.InterviewPosition;
import com.interview.entity.InterviewRecord;
import com.interview.entity.KnowledgeBase;
import com.interview.entity.RagRetrievalLog;
import com.interview.entity.RagRetrievalRequestLog;
import com.interview.mapper.InterviewPositionMapper;
import com.interview.mapper.KnowledgeBaseMapper;
import com.interview.mapper.RagRetrievalLogMapper;
import com.interview.mapper.RagRetrievalRequestLogMapper;
import com.interview.service.questionbank.QuestionBankService;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

@Service
@Slf4j
public class InterviewRetrievalService {

    private static final List<String> HR_SOFT_SKILL_CATEGORIES = List.of("HR软技能");
    private static final Pattern SENSITIVE_KEY_VALUE_PATTERN = Pattern.compile(
            "(?i)\\b(api[_-]?key|token|password|secret)\\s*([=:])\\s*([^\\s,;]+)");
    private static final Pattern URL_PATTERN = Pattern.compile("https?://\\S+", Pattern.CASE_INSENSITIVE);
    private static final String REMEDIAL_DIRECTIVE = """
            【本轮检索决策：补救追问】
            候选人当前回答信息不足，但题库召回上下文较可靠。请围绕最相关的 1 个知识点做一次低难度补救追问，帮助候选人说明基本概念、流程或经验；不要直接给出标准答案。
            """;
    private static final String SWITCH_TOPIC_DIRECTIVE = """
            【本轮检索决策：切换知识点】
            候选人连续低信息回答，或当前回答信息不足且题库召回置信度较低。请切换到同岗位另一个核心知识点，不要继续围绕上一题深挖。
            """;
    private static final String WEAK_RETRIEVAL_DIRECTIVE = """
            【本轮检索决策：召回置信度不足】
            本轮题库召回置信度不足。请主要依据面试历史、岗位配置和覆盖策略自然提问，不要强行使用题库上下文。
            """;

    private final QuestionBankService questionBankService;
    private final InterviewPositionMapper positionMapper;
    private final KnowledgeBaseMapper knowledgeBaseMapper;
    private final RagRetrievalLogMapper hitLogMapper;
    private final RagRetrievalRequestLogMapper requestLogMapper;
    private final AppEventService appEventService;
    private final RetrievalAnswerSignals answerSignals = new RetrievalAnswerSignals();

    @Value("${app.rag.retrieval-limit:20}")
    private int retrievalLimit = 20;

    @Value("${app.rag.retrieval-limit-max:30}")
    private int maxRetrievalLimit = 30;

    @Value("${app.rag.context-limit:10}")
    private int contextLimit = 10;

    @Value("${app.rag.high-confidence-score:0.70}")
    private double highConfidenceScore = 0.70;

    @Value("${app.rag.min-context-score:0.55}")
    private double minContextScore = 0.55;

    public InterviewRetrievalService(QuestionBankService questionBankService,
                                     InterviewPositionMapper positionMapper,
                                     KnowledgeBaseMapper knowledgeBaseMapper,
                                     RagRetrievalLogMapper hitLogMapper,
                                     RagRetrievalRequestLogMapper requestLogMapper,
                                     AppEventService appEventService) {
        this.questionBankService = questionBankService;
        this.positionMapper = positionMapper;
        this.knowledgeBaseMapper = knowledgeBaseMapper;
        this.hitLogMapper = hitLogMapper;
        this.requestLogMapper = requestLogMapper;
        this.appEventService = appEventService;
    }

    public TurnRetrieval retrieve(Long userId,
                                  InterviewRecord record,
                                  List<ChatMessage> chatHistory,
                                  String message,
                                  InterviewPhase nextPhase,
                                  List<String> usedAtomIds) {
        String position = record.getPosition() != null ? record.getPosition() : "common";
        String query = buildQuery(chatHistory, message);
        SearchScope searchScope = resolveSearchScope(userId, record.getPositionId());
        Long positionId = searchScope != null ? searchScope.positionId() : null;
        QuestionBankSearchRequest searchRequest = buildSearchRequest(position, query, message, usedAtomIds,
                searchScope, nextPhase);
        String requestId = UUID.randomUUID().toString();
        int turnIndex = chatHistory.size() / 2 + 1;
        String queryText = truncate(query, 500);
        int requestedLimit = searchRequest != null ? searchRequest.getLimit() : 0;
        List<QuestionBankSearchResult> candidates;
        long startedAt = System.nanoTime();

        if (searchRequest == null) {
            candidates = List.of();
            insertRequestLog(requestId, userId, record.getId(), turnIndex, position, positionId, nextPhase,
                    queryText, requestedLimit, 0, "SKIPPED", 0L, "SUCCESS", null);
        } else {
            try {
                QuestionBankSearchResponse response = questionBankService.searchWithMetadata(searchRequest);
                candidates = response.getResults() != null ? response.getResults() : List.of();
                long latencyMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);
                boolean degraded = "MYSQL_FALLBACK_DEGRADED".equals(response.getStrategy());
                insertRequestLog(requestId, userId, record.getId(), turnIndex, position, positionId, nextPhase,
                        queryText, requestedLimit, candidates.size(), response.getStrategy(), latencyMs,
                        degraded ? "DEGRADED" : "SUCCESS",
                        degraded ? "Qdrant unavailable; MySQL fallback used" : null);
            } catch (Exception e) {
                String sanitizedError = sanitizeErrorMessage(e.getMessage());
                log.warn("RAG 检索失败，跳过题库上下文: recordId={}, position={}, error={}",
                        record.getId(), position, sanitizedError);
                appEventService.recordSystemEvent(userId, "RAG_RETRIEVAL_FAILED", "system",
                        Map.of("recordId", record.getId(), "position", position), false, sanitizedError);
                candidates = List.of();
                long latencyMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);
                insertRequestLog(requestId, userId, record.getId(), turnIndex, position, positionId, nextPhase,
                        queryText, requestedLimit, 0, "FAILED", latencyMs, "FAILED", sanitizedError);
            }
        }

        RetrievalDecision decision = decide(message, chatHistory, candidates);
        TurnRetrieval retrieval = selectContext(candidates, decision);
        insertHitLogs(requestId, userId, record.getId(), turnIndex, queryText, position, positionId,
                candidates, retrieval.promptAtomIds());
        return retrieval;
    }

    private String buildQuery(List<ChatMessage> chatHistory, String message) {
        String query = message;
        for (int i = chatHistory.size() - 1; i >= 0; i--) {
            if (chatHistory.get(i) instanceof AiMessage aiMessage) {
                String previousQuestion = aiMessage.text();
                if (previousQuestion != null && !previousQuestion.isBlank()) {
                    query = previousQuestion.length() > 300
                            ? previousQuestion.substring(previousQuestion.length() - 300) + " " + message
                            : previousQuestion + " " + message;
                }
                break;
            }
        }
        return query;
    }

    private QuestionBankSearchRequest buildSearchRequest(String position, String query, String message,
                                                         List<String> usedAtomIds,
                                                         SearchScope searchScope,
                                                         InterviewPhase nextPhase) {
        if (nextPhase != InterviewPhase.TECHNICAL && nextPhase != InterviewPhase.HR) return null;
        if (searchScope == null) return null;
        QuestionBankSearchRequest request = new QuestionBankSearchRequest();
        request.setPosition(position);
        request.setScope(searchScope.scope());
        request.setOwnerUserId(searchScope.ownerUserId());
        request.setPositionId(searchScope.positionId());
        request.setKnowledgeBaseId(searchScope.knowledgeBaseId());
        request.setQuery(query);
        request.setExcludeAtomIds(usedAtomIds != null ? usedAtomIds : List.of());
        request.setLimit(answerSignals.effectiveRetrievalLimit(message, retrievalLimit, maxRetrievalLimit));
        if (nextPhase == InterviewPhase.HR) request.setCategories(HR_SOFT_SKILL_CATEGORIES);
        return request;
    }

    private SearchScope resolveSearchScope(Long userId, Long positionId) {
        if (positionId == null) {
            return null;
        }
        InterviewPosition position = positionMapper.selectById(positionId);
        if (position == null || !"ACTIVE".equalsIgnoreCase(position.getStatus())) {
            return null;
        }
        boolean privateOwner = "PRIVATE".equalsIgnoreCase(position.getScope())
                && userId != null
                && userId.equals(position.getOwnerUserId());
        boolean publicPosition = "PUBLIC".equalsIgnoreCase(position.getScope());
        if (!publicPosition && !privateOwner) {
            return null;
        }
        Long knowledgeBaseId = position.getDefaultKnowledgeBaseId();
        if (knowledgeBaseId == null) {
            KnowledgeBase knowledgeBase = knowledgeBaseMapper.selectOne(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<KnowledgeBase>()
                    .eq(KnowledgeBase::getPositionId, position.getId())
                    .eq(KnowledgeBase::getStatus, "ACTIVE")
                    .last("LIMIT 1"));
            knowledgeBaseId = knowledgeBase == null ? null : knowledgeBase.getId();
        }
        if (knowledgeBaseId == null) {
            return null;
        }
        return new SearchScope(position.getScope(), privateOwner ? position.getOwnerUserId() : null,
                position.getId(), knowledgeBaseId);
    }

    private void insertHitLogs(String requestId, Long userId, Long recordId, int turnIndex,
                               String queryText, String position, Long positionId,
                               List<QuestionBankSearchResult> candidates,
                               List<String> promptAtomIds) {
        Set<String> selected = new LinkedHashSet<>(promptAtomIds);
        int rank = 0;
        for (QuestionBankSearchResult result : candidates) {
            rank++;
            if (result.getAtomId() == null) continue;
            RagRetrievalLog logEntry = new RagRetrievalLog();
            logEntry.setUserId(userId);
            logEntry.setRecordId(recordId);
            logEntry.setRequestId(requestId);
            logEntry.setTurnIndex(turnIndex);
            logEntry.setQueryText(queryText);
            logEntry.setPosition(position);
            logEntry.setPositionId(positionId);
            logEntry.setRetrievedAtomId(result.getAtomId());
            logEntry.setRetrievedCategory(result.getCategory());
            logEntry.setSimilarityScore(result.getScore());
            logEntry.setRankIndex(rank);
            logEntry.setContextSelected(selected.contains(result.getAtomId()));
            try {
                hitLogMapper.insert(logEntry);
            } catch (Exception e) {
                log.warn("RAG 检索日志写入失败: {}", sanitizeErrorMessage(e.getMessage()));
            }
        }
    }

    private TurnRetrieval selectContext(List<QuestionBankSearchResult> candidates, RetrievalDecision decision) {
        StringBuilder context = new StringBuilder();
        List<String> promptAtomIds = new ArrayList<>();
        List<String> consumedAtomIds = new ArrayList<>();
        if (decision.directive() != null && !decision.directive().isBlank()) {
            context.append(decision.directive()).append("\n");
        }
        if (!decision.includeContext()) {
            return new TurnRetrieval(context.toString(), List.of(), List.of());
        }
        int count = Math.min(candidates.size(), normalizedContextLimit());
        for (int i = 0; i < count; i++) {
            QuestionBankSearchResult result = candidates.get(i);
            if (result.getScore() < minContextScore) continue;
            if (result.getAtomId() != null) {
                promptAtomIds.add(result.getAtomId());
                if (decision.consumeContext()) consumedAtomIds.add(result.getAtomId());
            }
            context.append(i + 1).append(". [atom_id: ")
                    .append(result.getAtomId() != null ? result.getAtomId() : "unknown")
                    .append("]\n").append(result.getPromptContext()).append("\n\n");
        }
        if (promptAtomIds.isEmpty() && context.isEmpty()) {
            context.append(WEAK_RETRIEVAL_DIRECTIVE).append("\n");
        }
        return new TurnRetrieval(context.toString(), List.copyOf(consumedAtomIds), List.copyOf(promptAtomIds));
    }

    private RetrievalDecision decide(String message, List<ChatMessage> chatHistory,
                                     List<QuestionBankSearchResult> candidates) {
        boolean lowInfoAnswer = answerSignals.isLowInformationAnswer(message);
        boolean consecutiveLowInfo = lowInfoAnswer && previousUserAnswerWasLowInformation(chatHistory);
        double topScore = candidates.stream()
                .mapToDouble(QuestionBankSearchResult::getScore)
                .max()
                .orElse(0.0);
        boolean confidentRetrieval = topScore >= highConfidenceScore;
        boolean usableRetrieval = topScore >= minContextScore;

        if (consecutiveLowInfo || (lowInfoAnswer && !usableRetrieval)) {
            return new RetrievalDecision(SWITCH_TOPIC_DIRECTIVE, false, false);
        }
        if (lowInfoAnswer) {
            return confidentRetrieval
                    ? new RetrievalDecision(REMEDIAL_DIRECTIVE, true, false)
                    : new RetrievalDecision(SWITCH_TOPIC_DIRECTIVE, false, false);
        }
        if (!usableRetrieval) {
            return new RetrievalDecision(WEAK_RETRIEVAL_DIRECTIVE, false, false);
        }
        return new RetrievalDecision("", true, true);
    }

    private boolean previousUserAnswerWasLowInformation(List<ChatMessage> chatHistory) {
        if (chatHistory == null || chatHistory.isEmpty()) return false;
        for (int i = chatHistory.size() - 1; i >= 0; i--) {
            if (chatHistory.get(i) instanceof UserMessage userMessage) {
                return answerSignals.isLowInformationAnswer(userMessage.singleText());
            }
        }
        return false;
    }

    private void insertRequestLog(String requestId, Long userId, Long recordId, int turnIndex,
                                  String position, Long positionId, InterviewPhase phase, String queryText,
                                  int requestedLimit, int candidateCount, String strategy,
                                  long latencyMs, String status, String errorMessage) {
        RagRetrievalRequestLog logEntry = new RagRetrievalRequestLog();
        logEntry.setRequestId(requestId);
        logEntry.setUserId(userId);
        logEntry.setRecordId(recordId);
        logEntry.setTurnIndex(turnIndex);
        logEntry.setPosition(position);
        logEntry.setPositionId(positionId);
        logEntry.setPhase(phase != null ? phase.name() : null);
        logEntry.setQueryText(queryText);
        logEntry.setRequestedLimit(requestedLimit);
        logEntry.setCandidateCount(candidateCount);
        logEntry.setRetrievalStrategy(strategy);
        logEntry.setLatencyMs(latencyMs);
        logEntry.setStatus(status);
        logEntry.setErrorMessage(errorMessage);
        try {
            requestLogMapper.insert(logEntry);
        } catch (Exception e) {
            log.warn("RAG 请求日志写入失败: {}", sanitizeErrorMessage(e.getMessage()));
        }
    }

    private int normalizedRetrievalLimit() {
        return answerSignals.normalizeLimit(retrievalLimit, maxRetrievalLimit);
    }

    private int normalizedMaxRetrievalLimit() {
        return answerSignals.normalizeMaxRetrievalLimit(maxRetrievalLimit);
    }

    private int normalizedContextLimit() {
        return Math.max(1, Math.min(contextLimit, normalizedMaxRetrievalLimit()));
    }

    private String sanitizeErrorMessage(String errorMessage) {
        if (errorMessage == null) return null;
        String sanitized = URL_PATTERN.matcher(errorMessage).replaceAll("[URL]");
        sanitized = SENSITIVE_KEY_VALUE_PATTERN.matcher(sanitized).replaceAll("$1$2[REDACTED]");
        return truncate(sanitized, 500);
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) return value;
        return value.substring(0, maxLength);
    }

    private record RetrievalDecision(String directive, boolean includeContext, boolean consumeContext) {
    }

    public record TurnRetrieval(String promptContext, List<String> contextAtomIds, List<String> promptAtomIds) {
        public static TurnRetrieval empty() {
            return new TurnRetrieval("", List.of(), List.of());
        }
    }

    private record SearchScope(String scope, Long ownerUserId, Long positionId, Long knowledgeBaseId) {
    }
}
