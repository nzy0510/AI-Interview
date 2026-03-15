package com.interview.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 面试记录实体
 * Phase 6 新增：abilityJson (六维能力评级JSON), recommendations (提升建议JSON列表), voiceWpm (语速)
 */
@Data
@TableName("interview_record")
public class InterviewRecord {
    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    /** 面试岗位，如 "Java后端开发" 或 "Web前端开发" */
    private String position;

    /** 完整对话历史，存储为 JSON 字符串 */
    private String chatHistory;

    /** AI 综合评分 (0-100) */
    private Integer score;

    /** AI 文字反馈与点评 */
    private String feedback;

    /**
     * 六维能力评级 JSON，格式如：
     * {"techDepth":"A","breadth":"B","problemSolving":"A","expression":"B","logic":"A","adaptability":"C"}
     */
    private String abilityJson;

    /**
     * AI 推荐的提升建议列表 JSON，格式如：
     * [{"period":"本周","action":"...","detail":"..."},...]
     */
    private String recommendations;

    /** 平均语速 (WPM - Words Per Minutes，实际用每分钟字符数估算) */
    private Integer voiceWpm;

    private LocalDateTime createTime;

    private LocalDateTime endTime;
}
