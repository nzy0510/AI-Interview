package com.interview.dto.questionbank;

import com.interview.entity.KnowledgeAtomImportBatch;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class QuestionBankBatchDetailResponse {
    private KnowledgeAtomImportBatch batch;
    private int atomCount;
    private int latestLinkedCount;
    private List<QuestionBankAtomListItem> atoms = new ArrayList<>();
}
