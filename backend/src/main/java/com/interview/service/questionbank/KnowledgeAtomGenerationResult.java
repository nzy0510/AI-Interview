package com.interview.service.questionbank;

public record KnowledgeAtomGenerationResult(
        Long sourceFileId,
        int received,
        int imported,
        boolean atomLimitReached
) {
}
