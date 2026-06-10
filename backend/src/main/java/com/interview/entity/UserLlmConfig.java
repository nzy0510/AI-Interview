package com.interview.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("user_llm_config")
public class UserLlmConfig {
    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private String provider;

    private String displayName;

    private String baseUrl;

    private String modelName;

    private String encryptedApiKey;

    private String apiKeyHint;

    private Double temperature;

    private Boolean active;

    private String lastTestStatus;

    private String lastTestMessage;

    private LocalDateTime lastTestTime;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
