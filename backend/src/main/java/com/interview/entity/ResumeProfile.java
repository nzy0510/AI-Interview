package com.interview.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 简历画像持久化实体
 * 每个用户在每岗位保留一份最新的简历解析画像，(userId, positionId) 唯一。
 */
@Data
@TableName("resume_profile")
public class ResumeProfile {
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 所属用户 ID */
    private Long userId;

    /** 结构化岗位 ID（Phase 2 起作为隔离键；历史行可为空） */
    private Long positionId;

    /** 面试目标岗位名称（上传时的快照） */
    private String position;

    /** AI 解析后的完整 JSON 画像（匹配度、技能、定制题等） */
    private String analysisJson;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
