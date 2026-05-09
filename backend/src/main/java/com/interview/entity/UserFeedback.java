package com.interview.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("user_feedback")
public class UserFeedback {
    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private String category;

    private String content;

    private String contact;

    private String pageUrl;

    private String status;

    private String ipHash;

    private String userAgentHash;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
