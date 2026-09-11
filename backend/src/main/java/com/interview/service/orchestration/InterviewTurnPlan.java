package com.interview.service.orchestration;

import com.interview.entity.InterviewPhase;

import java.util.List;
import java.util.Objects;

public record InterviewTurnPlan(
        InterviewPhase phase,
        InterviewAction action,
        String systemPrompt,
        String evidenceContext,
        List<String> evidenceAtomIds,
        List<String> consumedAtomIds) {

    public InterviewTurnPlan {
        Objects.requireNonNull(phase, "phase");
        Objects.requireNonNull(action, "action");
        systemPrompt = Objects.requireNonNullElse(systemPrompt, "");
        evidenceContext = Objects.requireNonNullElse(evidenceContext, "");
        evidenceAtomIds = List.copyOf(Objects.requireNonNullElse(evidenceAtomIds, List.of()));
        consumedAtomIds = List.copyOf(Objects.requireNonNullElse(consumedAtomIds, List.of()));
    }
}
