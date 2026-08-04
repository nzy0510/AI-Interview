package com.interview.service.orchestration;

import com.interview.entity.InterviewPhase;

import java.util.List;
import java.util.Objects;

public record InterviewTurnRequest(
        Long recordId,
        Long userId,
        Long positionId,
        String positionName,
        InterviewPhase currentPhase,
        int turnIndex,
        String difficultyLevel,
        List<String> focusAreas,
        List<InterviewMessageSnapshot> recentHistory,
        String latestAnswer,
        List<String> usedAtomIds,
        List<String> tailoredQuestions) {

    public InterviewTurnRequest {
        Objects.requireNonNull(recordId, "recordId");
        Objects.requireNonNull(userId, "userId");
        Objects.requireNonNull(positionId, "positionId");
        Objects.requireNonNull(currentPhase, "currentPhase");
        positionName = Objects.requireNonNullElse(positionName, "");
        difficultyLevel = Objects.requireNonNullElse(difficultyLevel, "mid");
        focusAreas = List.copyOf(Objects.requireNonNullElse(focusAreas, List.of()));
        recentHistory = List.copyOf(Objects.requireNonNullElse(recentHistory, List.of()));
        latestAnswer = Objects.requireNonNullElse(latestAnswer, "");
        usedAtomIds = List.copyOf(Objects.requireNonNullElse(usedAtomIds, List.of()));
        tailoredQuestions = List.copyOf(Objects.requireNonNullElse(tailoredQuestions, List.of()));
    }
}
