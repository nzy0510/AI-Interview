package com.interview.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("rag_retrieval_request_log")
public class RagRetrievalRequestLog {
    @TableId(type = IdType.AUTO)
    private Long id;

    private String requestId;

    private Long userId;

    private Long recordId;

    private Integer turnIndex;

    private String position;

    private Long positionId;

    private String phase;

    private String queryText;

    private Integer requestedLimit;

    private Integer candidateCount;

    private String retrievalStrategy;

    private Long latencyMs;

    private String status;

    private String errorMessage;

    private LocalDateTime createTime;
}
