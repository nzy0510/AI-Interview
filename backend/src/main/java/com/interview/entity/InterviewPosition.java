package com.interview.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("interview_position")
public class InterviewPosition {
    @TableId(type = IdType.AUTO)
    private Long id;

    private String scope;
    private Long ownerUserId;
    private String name;
    private String description;
    private String status;
    private Long defaultKnowledgeBaseId;
    private Long createdBy;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
