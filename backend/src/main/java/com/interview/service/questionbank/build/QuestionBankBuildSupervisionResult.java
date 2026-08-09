package com.interview.service.questionbank.build;

import java.util.List;
import java.util.Map;

record QuestionBankBuildSupervisionResult(
        String status,
        double score,
        List<String> issues,
        Map<String, Object> suggestedPatch,
        String duplicateHint,
        String rawJson
) {
    static QuestionBankBuildSupervisionResult needsHuman(String issue) {
        return new QuestionBankBuildSupervisionResult(
                "NEEDS_HUMAN",
                0.0,
                List.of(issue),
                Map.of(),
                null,
                null
        );
    }
}
