package com.interview.dto.questionbank;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Data
public class QuestionBankImportRequest {
    private String batchId;
    private String sourceRef;
    private String targetCategory;
    private String mode = "DRAFT";
    private List<KnowledgeAtomPayload> atoms = new ArrayList<>();
    private Map<String, Object> validationReport;
    private Map<String, Object> reviewReport;
}
