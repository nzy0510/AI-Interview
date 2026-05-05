package com.interview.dto.questionbank;

import lombok.Builder;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder
public class QuestionBankImportResult {
    private String batchId;
    private String mode;
    private int received;
    private int imported;
    private int published;
    private int failed;
    @Builder.Default
    private List<String> errors = new ArrayList<>();
}
