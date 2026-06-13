package com.interview.service.questionbank;

import com.interview.entity.KnowledgeSourceFile;

public interface DocumentConverterClient {
    String convertToMarkdown(KnowledgeSourceFile sourceFile);
}
