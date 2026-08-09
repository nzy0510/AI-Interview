package com.interview.dto.questionbank.build;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class QuestionBankBuildFinalizationRequest {
    private List<Long> candidateIds = new ArrayList<>();
    private Long expectedReviewRevision;
}
