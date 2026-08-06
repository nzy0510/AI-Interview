package com.interview.service.questionbank.build;

import com.interview.service.UserLlmRuntimeConfig;

public interface QuestionBankBuildLlm {
    String complete(UserLlmRuntimeConfig config, String systemPrompt, String userPrompt);
}
