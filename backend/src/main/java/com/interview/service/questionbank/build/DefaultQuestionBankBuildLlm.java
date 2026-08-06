package com.interview.service.questionbank.build;

import com.interview.service.UserLlmModelFactory;
import com.interview.service.UserLlmRuntimeConfig;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;

@Service
public class DefaultQuestionBankBuildLlm implements QuestionBankBuildLlm {
    private final UserLlmModelFactory modelFactory;

    public DefaultQuestionBankBuildLlm(UserLlmModelFactory modelFactory) {
        this.modelFactory = modelFactory;
    }

    @Override
    public String complete(UserLlmRuntimeConfig config, String systemPrompt, String userPrompt) {
        var response = modelFactory.createChatModel(config, Duration.ofSeconds(180)).generate(List.of(
                SystemMessage.from(systemPrompt),
                UserMessage.from(userPrompt)
        ));
        if (response == null || response.content() == null || response.content().text() == null) {
            throw new IllegalStateException("模型未返回有效内容");
        }
        return response.content().text();
    }
}
