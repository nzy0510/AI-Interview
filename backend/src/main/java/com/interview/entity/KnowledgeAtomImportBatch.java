package com.interview.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("knowledge_atom_import_batch")
public class KnowledgeAtomImportBatch {
    @TableId(type = IdType.AUTO)
    private Long id;

    private String batchId;
    private String sourceRef;
    private String targetCategory;
    private String mode;
    private String status;
    private Integer atomCount;
    private String validationReport;
    private String reviewReport;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
