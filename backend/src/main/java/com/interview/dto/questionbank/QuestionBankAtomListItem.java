package com.interview.dto.questionbank;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class QuestionBankAtomListItem {
    private Long id;
    private String atomId;
    private String subject;
    private String category;
    private String difficulty;
    private String status;
    private String vectorStatus;
    private String sourceRef;
    private Long sourceFileId;
    private String sourceEvidenceJson;
    private String reviewStatus;
    private String reviewReason;
    private Double reviewConfidence;
    private Long reviewedBy;
    private LocalDateTime reviewedAt;
    private String suggestedPatchJson;
    private String publicationStatus;
    private Integer currentVersionNo;
    private LocalDateTime lastIndexedAt;
    private LocalDateTime updateTime;
}
