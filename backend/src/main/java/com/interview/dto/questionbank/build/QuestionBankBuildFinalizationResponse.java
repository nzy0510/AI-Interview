package com.interview.dto.questionbank.build;

import lombok.Data;

@Data
public class QuestionBankBuildFinalizationResponse {
    private Long buildId;
    private Long jobId;
    private String status;
    private String stage;
    private String finalizationStatus;
    private Integer selectedCount;
}
