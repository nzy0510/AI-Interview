package com.interview.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Daily usage snapshot for costly actions guarded by Redis quota counters.
 */
@Data
@TableName("user_daily_usage")
public class UserDailyUsage {
    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private LocalDate usageDate;

    private String quotaType;

    private Integer usedCount;

    private Integer limitCount;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
