package com.interview.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("question_bank_build")
public class QuestionBankBuild {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String scope;
    private Long ownerUserId;
    private Long positionId;
    private Long knowledgeBaseId;
    private String status;
    private String stage;
    private Integer progress;
    private String categoriesJson;
    private Long llmConfigId;
    private String llmProvider;
    private String llmModel;
    private String llmRuntimeFingerprint;
    private String promptVersion;
    private Integer chunkCount;
    private Integer completedChunkCount;
    private String checkpointJson;
    private Integer candidateCount;
    private Integer acceptedCount;
    private Integer rejectedCount;
    private Integer autoPassCount;
    private Integer needsHumanCount;
    private Integer autoRejectCount;
    private Integer repairRound;
    private Integer repairedCount;
    private Integer repairFailedCount;
    private Long reviewRevision;
    private String finalizationStatus;
    private String finalImportBatchId;
    private String finalAtomIdsJson;
    private String finalizationResultJson;
    private Long finalizedBy;
    private LocalDateTime finalizedAt;
    private String errorMessage;
    private Long createdBy;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
