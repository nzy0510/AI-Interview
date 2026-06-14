package com.interview.service.questionbank;

import com.interview.service.UserLlmRuntimeConfig;

public interface KnowledgeAtomAiClient {
    KnowledgeAtomDraftBundle generateReviewedAtoms(UserLlmRuntimeConfig runtimeConfig, String markdown);
}
