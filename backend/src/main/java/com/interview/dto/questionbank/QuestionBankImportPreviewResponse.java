package com.interview.dto.questionbank;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class QuestionBankImportPreviewResponse {
    private String batchId;
    private String mode;
    private String targetCategory;
    private String sourceRef;
    private int received;
    private int newCount;
    private int updateCount;
    private boolean batchIdExists;
    private List<String> newAtomIds = new ArrayList<>();
    private List<String> updateAtomIds = new ArrayList<>();
    private List<String> duplicateAtomIds = new ArrayList<>();
    private List<String> errors = new ArrayList<>();
}
