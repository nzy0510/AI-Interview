package com.interview.dto.questionbank;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class QuestionBankAtomListItem {
    private String atomId;
    private String subject;
    private String category;
    private String difficulty;
    private String status;
    private String vectorStatus;
    private String sourceRef;
    private LocalDateTime lastIndexedAt;
    private LocalDateTime updateTime;
}
