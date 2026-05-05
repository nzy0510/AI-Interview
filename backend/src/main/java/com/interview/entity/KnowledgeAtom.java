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
    private LocalDateTime lastIndexedAt;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
