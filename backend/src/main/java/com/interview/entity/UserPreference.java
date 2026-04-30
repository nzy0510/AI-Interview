package com.interview.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("user_preference")
public class UserPreference {
    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private String defaultMode;

    private String defaultRole;

    private String focusAreas;

    private String difficultyLevel;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
