package com.interview.service.questionbank.build;

import com.interview.entity.QuestionBankBuild;
import com.interview.entity.QuestionBankBuildCandidate;
import com.interview.service.UserLlmRuntimeConfig;

import java.util.List;

record QuestionBankBuildSupervisionContext(
        QuestionBankBuild build,
        QuestionBankBuildCandidate candidate,
        String sourceText,
        UserLlmRuntimeConfig runtime,
        List<QuestionBankBuildCandidate> batchCandidates
) {
}
