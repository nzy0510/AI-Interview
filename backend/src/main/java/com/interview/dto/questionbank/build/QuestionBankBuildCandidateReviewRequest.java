package com.interview.dto.questionbank.build;

import lombok.Data;

import java.util.List;

@Data
public class QuestionBankBuildCandidateReviewRequest {
    private String action;
    private String subject;
    private String category;
    private String difficulty;
    private List<String> tags;
    private String principles;
    private String pitfalls;
    private List<String> followUpPaths;
}
