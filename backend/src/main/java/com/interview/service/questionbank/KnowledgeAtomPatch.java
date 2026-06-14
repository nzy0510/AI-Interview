package com.interview.service.questionbank;

import java.util.List;

public record KnowledgeAtomPatch(
        String subject,
        String category,
        String difficulty,
        List<String> tags,
        String principles,
        String pitfalls,
        List<String> followUpPaths
) {
}
