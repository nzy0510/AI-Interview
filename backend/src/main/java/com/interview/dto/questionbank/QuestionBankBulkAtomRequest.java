package com.interview.dto.questionbank;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class QuestionBankBulkAtomRequest {
    private List<String> atomIds = new ArrayList<>();
}
