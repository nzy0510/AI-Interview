package com.interview.dto.questionbank;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class QuestionBankSearchRequest {
    private String position;
    private String query;
    private String scope;
    private Long ownerUserId;
    private Long positionId;
    private Long knowledgeBaseId;
    private List<String> categories = new ArrayList<>();
    private String difficulty;
    private List<String> excludeAtomIds = new ArrayList<>();
    private int limit = 3;
}
