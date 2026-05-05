package com.interview.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("knowledge_atom_review")
public class KnowledgeAtomReview {
    @TableId(type = IdType.AUTO)
    private Long id;

    private String batchId;
    private String atomId;
    private String verdict;
    private Integer score;
    private String issuesJson;
    private LocalDateTime createTime;
}
