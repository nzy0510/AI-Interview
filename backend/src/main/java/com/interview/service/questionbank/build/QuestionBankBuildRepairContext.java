package com.interview.service.questionbank.build;

import com.interview.entity.QuestionBankBuild;
import com.interview.entity.QuestionBankBuildCandidate;
import com.interview.service.UserLlmRuntimeConfig;

record QuestionBankBuildRepairContext(
        QuestionBankBuild build,
        QuestionBankBuildCandidate candidate,
        String sourceText,
        UserLlmRuntimeConfig runtime,
        int round
) {
}
