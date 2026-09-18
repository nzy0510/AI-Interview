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
        return new InterviewTurnPlan(
                promptPlan.phase(),
                retrieval.action(),
                promptPlan.systemPrompt(),
                retrieval.promptContext(),
                retrieval.promptAtomIds(),
                retrieval.contextAtomIds());
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
}
