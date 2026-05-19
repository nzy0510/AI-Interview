package com.interview.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("mcp_quota_policy")
public class McpQuotaPolicy {
    @TableId(type = IdType.AUTO)
    private Long id;

    private String roleName;

    private String quotaType;

    private String label;

    private Integer limitCount;

    private Integer displayOrder;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
