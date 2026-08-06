package com.interview.dto.questionbank.build;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Data
public class QuestionBankBuildCandidateResponse {
    private Long candidateId;
    private String stableAtomId;
    private Long buildId;
    private Integer chunkIndex;
    private Long sourceFileId;
    private String sourceRef;
    private List<SourceEvidence> sourceEvidence = new ArrayList<>();
    private String subject;
    private String category;
    private String difficulty;
    private List<String> tags = new ArrayList<>();
    private String principles;
    private String pitfalls;
    private List<String> followUpPaths = new ArrayList<>();
    private Map<String, Object> selfCheck;
    private String duplicateHint;
    private String reviewStatus;
    private String reviewReason;
    private LocalDateTime updatedAt;

    @Data
    public static class SourceEvidence {
        private String quote;
        private String pageOrSection;
    }
}
