package com.interview.dto.questionbank;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class QuestionBankBatchListItem {
    private String batchId;
    private String sourceRef;
    private String targetCategory;
    private String mode;
    private String status;
    private Integer atomCount;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
