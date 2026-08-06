package com.interview.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "app.question-bank.build")
public class QuestionBankBuildProperties {
    private String storageRoot = "knowledge-storage";
    private long maxFileSizeBytes = 20L * 1024 * 1024;
    private int maxFiles = 10;
    private int maxTextChars = 1_000_000;
    private int chunkChars = 8_000;
    private int chunkOverlapChars = 400;
    private int maxChunks = 50;
    private int maxCandidates = 300;
    private String promptVersion = "question-bank-build-v1";
}
