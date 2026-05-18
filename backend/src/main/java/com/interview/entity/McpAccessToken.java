package com.interview.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("mcp_access_token")
public class McpAccessToken {
    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private String tokenHash;

    private String tokenPrefix;

    private String role;

    private String status;

    private LocalDateTime lastUsedAt;

    private LocalDateTime revokedAt;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
