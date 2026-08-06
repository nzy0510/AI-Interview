package com.interview.dto.questionbank.build;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
public class QuestionBankBuildResponse {
    private Long buildId;
    private Long jobId;
    private String status;
    private String stage;
    private Integer progress;
    private Long ownerUserId;
    private Long positionId;
    private Long knowledgeBaseId;
    private List<String> categories = new ArrayList<>();
    private List<SourceFileResponse> sourceFiles = new ArrayList<>();
    private Integer fileCount;
    private Integer chunkCount;
    private Integer estimatedCallCount;
    private Integer completedChunkCount;
    private Integer candidateCount;
    private Integer acceptedCount;
    private Integer rejectedCount;
    private Long configId;
    private String provider;
    private String model;
    private String promptVersion;
    private String errorMessage;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    @Data
    public static class SourceFileResponse {
        private Long sourceFileId;
        private String originalFilename;
        private Long size;
        private String contentType;
        private String status;
    }
}
