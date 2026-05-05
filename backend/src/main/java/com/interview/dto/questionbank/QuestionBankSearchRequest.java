package com.interview.dto.questionbank;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class QuestionBankSearchRequest {
    private String position;
    private String query;
    private List<String> categories = new ArrayList<>();
    private String difficulty;
    private List<String> excludeAtomIds = new ArrayList<>();
    private int limit = 3;
}
