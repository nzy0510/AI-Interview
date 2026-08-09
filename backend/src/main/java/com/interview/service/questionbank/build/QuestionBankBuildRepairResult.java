package com.interview.service.questionbank.build;

import java.util.List;

record QuestionBankBuildRepairResult(
        String action,
        String subject,
        String category,
        String difficulty,
        List<String> tags,
        String principles,
        String pitfalls,
        List<String> followUpPaths,
        String summary,
        String rawResponse
) {
}
