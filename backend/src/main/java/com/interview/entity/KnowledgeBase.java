package com.interview.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("knowledge_base")
public class KnowledgeBase {
    @TableId(type = IdType.AUTO)
    private Long id;

    private String scope;
    private Long ownerUserId;
    private Long positionId;
    private String name;
    private String status;
    private Long createdBy;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
