package com.interview.dto.questionbank;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class QuestionBankSearchResponse {
    private List<QuestionBankSearchResult> results;
    private String strategy;
}
