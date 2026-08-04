package com.interview.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("interview_turn")
public class InterviewTurn {
    @TableId(type = IdType.AUTO)
    private Long id;

    private Long recordId;
    private Long userId;
    private Long positionId;
    private Integer turnIndex;
    private String phase;
    private String aiQuestion;
    private String userAnswer;
    private String retrievedAtomIds;
    private String contextSnapshotJson;
    private String retrievalStrategy;
    private String orchestrationMode;
    private String decisionAction;
    private String decisionJson;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
