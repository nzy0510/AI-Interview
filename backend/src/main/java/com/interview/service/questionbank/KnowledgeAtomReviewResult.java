package com.interview.service.questionbank;

public record KnowledgeAtomReviewResult(
        String status,
        String reason,
        Double confidence,
        KnowledgeAtomPatch suggestedPatch
) {
}
