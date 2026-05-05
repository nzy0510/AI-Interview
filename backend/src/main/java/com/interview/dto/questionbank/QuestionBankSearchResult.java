package com.interview.dto.questionbank;

import com.interview.entity.KnowledgeAtom;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class QuestionBankSearchResult {
    private String atomId;
    private String subject;
    private String category;
    private String difficulty;
    private double score;
    private String promptContext;
    private KnowledgeAtom atom;
}
