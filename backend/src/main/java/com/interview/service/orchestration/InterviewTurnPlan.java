package com.interview.service.orchestration;

import com.interview.entity.InterviewPhase;

import java.util.List;
import java.util.Objects;

public record InterviewTurnPlan(
        InterviewPhase phase,
        InterviewAction action,
        OrchestrationMode orchestrationMode,
        String systemPrompt,
        String evidenceContext,
        List<String> evidenceAtomIds,
        List<String> consumedAtomIds,
        List<String> toolsUsed,
        String publicSummary,
        String fallbackReasonCode) {

    public InterviewTurnPlan {
        Objects.requireNonNull(phase, "phase");
        Objects.requireNonNull(action, "action");
        Objects.requireNonNull(orchestrationMode, "orchestrationMode");
        systemPrompt = Objects.requireNonNullElse(systemPrompt, "");
        evidenceContext = Objects.requireNonNullElse(evidenceContext, "");
        evidenceAtomIds = List.copyOf(Objects.requireNonNullElse(evidenceAtomIds, List.of()));
        consumedAtomIds = List.copyOf(Objects.requireNonNullElse(consumedAtomIds, List.of()));
        toolsUsed = List.copyOf(Objects.requireNonNullElse(toolsUsed, List.of()));
        publicSummary = Objects.requireNonNullElse(publicSummary, "");
    }
}
