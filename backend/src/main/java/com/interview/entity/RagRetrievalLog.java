package com.interview.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("rag_retrieval_log")
public class RagRetrievalLog {
    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private Long recordId;

    private String requestId;

    private Integer turnIndex;

    private String queryText;

    private String position;

    private String retrievedAtomId;

    private String retrievedCategory;

    private Double similarityScore;

    private Integer rankIndex;

    private Boolean contextSelected;

    private LocalDateTime createTime;
}
