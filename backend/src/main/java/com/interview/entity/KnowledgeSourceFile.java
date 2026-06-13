package com.interview.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("knowledge_source_file")
public class KnowledgeSourceFile {
    @TableId(type = IdType.AUTO)
    private Long id;

    private String scope;
    private Long ownerUserId;
    private Long positionId;
    private Long knowledgeBaseId;
    private String originalFilename;
    private String contentType;
    private Long fileSize;
    private String fileHash;
    private String storageKey;
    private String markdownStorageKey;
    private String domainTagsJson;
    private String status;
    private String errorMessage;
    private Long createdBy;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
