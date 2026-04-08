package com.interview.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 简历画像持久化实体
 * 每个用户保存一份最新的简历解析画像，支持更新覆盖
 */
@Data
@TableName("resume_profile")
public class ResumeProfile {
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 所属用户 ID */
    private Long userId;

    /** 面试目标岗位 */
    private String position;

    /** AI 解析后的完整 JSON 画像（匹配度、技能、定制题等） */
    private String analysisJson;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
