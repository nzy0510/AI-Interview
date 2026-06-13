package com.interview.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("knowledge_atom")
public class KnowledgeAtom {
    @TableId(type = IdType.AUTO)
    private Long id;

    private String atomId;
    private String subject;
    private String category;
    private String difficulty;
    private String tagsJson;
    private String principles;
    private String pitfalls;
    private String followUpPathsJson;
    private String status;
    private String sourceRef;
    private String checksum;
    private String vectorStatus;
    private String scope;
    private Long ownerUserId;
    private Long positionId;
    private Long knowledgeBaseId;
    private Long sourceFileId;
    private Integer currentVersionNo;
    private String reviewStatus;
    private String reviewReason;
    private Double reviewConfidence;
    private String suggestedPatchJson;
    private String publicationStatus;
    private Long publishedBy;
    private LocalDateTime publishedAt;
    private Long reviewedBy;
    private LocalDateTime reviewedAt;
    private String vectorErrorMessage;
    private LocalDateTime lastIndexedAt;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
