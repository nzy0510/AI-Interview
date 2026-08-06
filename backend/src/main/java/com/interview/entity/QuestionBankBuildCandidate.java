package com.interview.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("question_bank_build_candidate")
public class QuestionBankBuildCandidate {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long buildId;
    private Long ownerUserId;
    private Long positionId;
    private Long knowledgeBaseId;
    private Long sourceFileId;
    private Integer chunkIndex;
    private String stableAtomId;
    private String sourceRef;
    private String subject;
    private String category;
    private String difficulty;
    private String tagsJson;
    private String principles;
    private String pitfalls;
    private String followUpPathsJson;
    private String sourceEvidenceJson;
    private String selfCheckJson;
    private String duplicateHint;
    private String reviewStatus;
    private String reviewReason;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
