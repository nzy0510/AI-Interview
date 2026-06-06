package com.interview.service;

import com.interview.dto.questionbank.QuestionBankSearchRequest;
import com.interview.dto.questionbank.QuestionBankSearchResponse;
import com.interview.dto.questionbank.QuestionBankSearchResult;
import com.interview.entity.InterviewPhase;
import com.interview.entity.InterviewRecord;
import com.interview.entity.RagRetrievalLog;
import com.interview.entity.RagRetrievalRequestLog;
import com.interview.mapper.RagRetrievalLogMapper;
import com.interview.mapper.RagRetrievalRequestLogMapper;
import com.interview.service.questionbank.QuestionBankService;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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

    private final QuestionBankService questionBankService;
    private final RagRetrievalLogMapper hitLogMapper;
    private final RagRetrievalRequestLogMapper requestLogMapper;
    private final AppEventService appEventService;

    @Value("${app.rag.retrieval-limit:20}")
    private int retrievalLimit = 20;

    @Value("${app.rag.context-limit:10}")
    private int contextLimit = 10;

    public InterviewRetrievalService(QuestionBankService questionBankService,
                                     RagRetrievalLogMapper hitLogMapper,
                                     RagRetrievalRequestLogMapper requestLogMapper,
                                     AppEventService appEventService) {
        this.questionBankService = questionBankService;
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
        QuestionBankSearchRequest searchRequest = buildSearchRequest(position, query, usedAtomIds, nextPhase);
        String requestId = UUID.randomUUID().toString();
        int turnIndex = chatHistory.size() / 2 + 1;
        String queryText = truncate(query, 500);
        int requestedLimit = searchRequest != null ? searchRequest.getLimit() : 0;
        List<QuestionBankSearchResult> candidates;
        long startedAt = System.nanoTime();

        if (searchRequest == null) {
            candidates = List.of();
            insertRequestLog(requestId, userId, record.getId(), turnIndex, position, nextPhase,
                    queryText, requestedLimit, 0, "SKIPPED", 0L, "SUCCESS", null);
        } else {
            try {
                QuestionBankSearchResponse response = questionBankService.searchWithMetadata(searchRequest);
                candidates = response.getResults() != null ? response.getResults() : List.of();
                long latencyMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);
                boolean degraded = "MYSQL_FALLBACK_DEGRADED".equals(response.getStrategy());
                insertRequestLog(requestId, userId, record.getId(), turnIndex, position, nextPhase,
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
                insertRequestLog(requestId, userId, record.getId(), turnIndex, position, nextPhase,
                        queryText, requestedLimit, 0, "FAILED", latencyMs, "FAILED", sanitizedError);
            }
        }

        insertHitLogs(requestId, userId, record.getId(), turnIndex, queryText, position, candidates);
        return selectContext(candidates);
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

    private QuestionBankSearchRequest buildSearchRequest(String position, String query,
                                                         List<String> usedAtomIds,
                                                         InterviewPhase nextPhase) {
        if (nextPhase != InterviewPhase.TECHNICAL && nextPhase != InterviewPhase.HR) return null;
        QuestionBankSearchRequest request = new QuestionBankSearchRequest();
        request.setPosition(position);
        request.setQuery(query);
        request.setExcludeAtomIds(usedAtomIds != null ? usedAtomIds : List.of());
        request.setLimit(normalizedRetrievalLimit());
        if (nextPhase == InterviewPhase.HR) request.setCategories(HR_SOFT_SKILL_CATEGORIES);
        return request;
    }

    private void insertHitLogs(String requestId, Long userId, Long recordId, int turnIndex,
                               String queryText, String position,
                               List<QuestionBankSearchResult> candidates) {
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
            logEntry.setRetrievedAtomId(result.getAtomId());
            logEntry.setRetrievedCategory(result.getCategory());
            logEntry.setSimilarityScore(result.getScore());
            logEntry.setRankIndex(rank);
            logEntry.setContextSelected(rank <= normalizedContextLimit());
            try {
                hitLogMapper.insert(logEntry);
            } catch (Exception e) {
                log.warn("RAG 检索日志写入失败: {}", sanitizeErrorMessage(e.getMessage()));
            }
        }
    }

    private TurnRetrieval selectContext(List<QuestionBankSearchResult> candidates) {
        StringBuilder context = new StringBuilder();
        List<String> atomIds = new ArrayList<>();
        int count = Math.min(candidates.size(), normalizedContextLimit());
        for (int i = 0; i < count; i++) {
            QuestionBankSearchResult result = candidates.get(i);
            if (result.getAtomId() != null) atomIds.add(result.getAtomId());
            context.append(i + 1).append(". [atom_id: ")
                    .append(result.getAtomId() != null ? result.getAtomId() : "unknown")
                    .append("]\n").append(result.getPromptContext()).append("\n\n");
        }
        return new TurnRetrieval(context.toString(), List.copyOf(atomIds));
    }

    private void insertRequestLog(String requestId, Long userId, Long recordId, int turnIndex,
                                  String position, InterviewPhase phase, String queryText,
                                  int requestedLimit, int candidateCount, String strategy,
                                  long latencyMs, String status, String errorMessage) {
        RagRetrievalRequestLog logEntry = new RagRetrievalRequestLog();
        logEntry.setRequestId(requestId);
        logEntry.setUserId(userId);
        logEntry.setRecordId(recordId);
        logEntry.setTurnIndex(turnIndex);
        logEntry.setPosition(position);
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
        return Math.max(1, Math.min(retrievalLimit, 20));
    }

    private int normalizedContextLimit() {
        return Math.max(1, Math.min(contextLimit, normalizedRetrievalLimit()));
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

    public record TurnRetrieval(String promptContext, List<String> contextAtomIds) {
        public static TurnRetrieval empty() {
            return new TurnRetrieval("", List.of());
        }
    }
}
