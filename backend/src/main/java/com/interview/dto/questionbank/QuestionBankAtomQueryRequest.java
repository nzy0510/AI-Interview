package com.interview.dto.questionbank;

import lombok.Data;

@Data
public class QuestionBankAtomQueryRequest {
    private String keyword;
    private String category;
    private String status;
    private String difficulty;
    private String vectorStatus;
    private String sourceRef;
    private String batchId;
    private int page = 1;
    private int size = 20;
}
