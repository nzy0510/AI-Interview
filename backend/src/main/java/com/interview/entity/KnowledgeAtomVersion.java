package com.interview.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("knowledge_atom_version")
public class KnowledgeAtomVersion {
    @TableId(type = IdType.AUTO)
    private Long id;

    private String atomId;
    private Integer versionNo;
    private String snapshotJson;
    private String changeReason;
    private LocalDateTime createTime;
}
