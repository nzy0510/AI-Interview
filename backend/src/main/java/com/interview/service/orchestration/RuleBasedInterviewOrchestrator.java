package com.interview.service.orchestration;

import com.alibaba.fastjson2.JSON;
import com.interview.entity.InterviewPhase;
import com.interview.entity.InterviewRecord;
import com.interview.service.InterviewRetrievalService;
import com.interview.service.InterviewTurnPlanner;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service("ruleBasedInterviewOrchestrator")
public class RuleBasedInterviewOrchestrator implements InterviewOrchestrator {

    private static final String REMEDIAL_MARKER = "本轮检索决策：补救追问";
    private static final String SWITCH_MARKER = "本轮检索决策：切换知识点";

    private final InterviewRetrievalService retrievalService;
    private final InterviewTurnPlanner turnPlanner;

    public RuleBasedInterviewOrchestrator(InterviewRetrievalService retrievalService,
                                          InterviewTurnPlanner turnPlanner) {
        this.retrievalService = retrievalService;
        this.turnPlanner = turnPlanner;
    }

    @Override
    public InterviewTurnPlan plan(InterviewTurnRequest request) {
        InterviewRecord record = toRecord(request);
        List<ChatMessage> history = toChatHistory(request.recentHistory());
        InterviewPhase nextPhase = turnPlanner.determineNextPhase(record, history);
        InterviewRetrievalService.TurnRetrieval retrieval = retrievalService.retrieve(
                request.userId(), record, history, request.latestAnswer(), nextPhase, request.usedAtomIds());
        InterviewTurnPlanner.InterviewTurnPlan promptPlan = turnPlanner.plan(
                record, history, retrieval.promptContext(), request.tailoredQuestions());
        InterviewAction action = actionFor(retrieval.promptContext());

        return new InterviewTurnPlan(
                promptPlan.phase(),
                action,
                OrchestrationMode.RULE,
                promptPlan.systemPrompt(),
                retrieval.promptContext(),
                retrieval.promptAtomIds(),
                retrieval.contextAtomIds(),
                List.of(),
                summaryFor(action),
                null);
    }

    private InterviewRecord toRecord(InterviewTurnRequest request) {
        InterviewRecord record = new InterviewRecord();
        record.setId(request.recordId());
        record.setUserId(request.userId());
        record.setPositionId(request.positionId());
        record.setPosition(request.positionName());
        record.setPhase(request.currentPhase().name());
        record.setDifficultyLevel(request.difficultyLevel());
        record.setFocusAreas(JSON.toJSONString(request.focusAreas()));
        return record;
    }

    private List<ChatMessage> toChatHistory(List<InterviewMessageSnapshot> snapshots) {
        List<ChatMessage> messages = new ArrayList<>();
        for (InterviewMessageSnapshot snapshot : snapshots) {
            switch (snapshot.role()) {
                case "USER" -> messages.add(new UserMessage(snapshot.content()));
                case "AI" -> messages.add(new AiMessage(snapshot.content()));
                case "SYSTEM" -> messages.add(new SystemMessage(snapshot.content()));
                default -> {
                    // Unknown roles are intentionally excluded from the model context.
                }
            }
        }
        return messages;
    }

    private InterviewAction actionFor(String context) {
        if (context != null && context.contains(REMEDIAL_MARKER)) {
            return InterviewAction.REMEDIATE;
        }
        if (context != null && context.contains(SWITCH_MARKER)) {
            return InterviewAction.SWITCH_TOPIC;
        }
        return InterviewAction.CONTINUE_PHASE;
    }

    private String summaryFor(InterviewAction action) {
        return switch (action) {
            case REMEDIATE -> "使用稳定规则进行基础补救追问";
            case SWITCH_TOPIC -> "使用稳定规则切换到新的岗位知识点";
            default -> "使用稳定规则继续当前面试阶段";
        };
    }
}
