package com.interview.service.questionbank;

import java.util.List;

public record KnowledgeAtomDraft(
        String subject,
        String category,
        String difficulty,
        List<String> tags,
        String principles,
        String pitfalls,
        List<String> followUpPaths,
        KnowledgeAtomReviewResult review
) {
}
