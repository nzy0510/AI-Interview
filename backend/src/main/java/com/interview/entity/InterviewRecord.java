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

    /**
     * 视频面试情感分析数据 JSON，格式如：
     * {"avgConfidence":0.72,"dominantEmotion":"neutral","emotionDistribution":{"neutral":0.6,"happy":0.2,...},"timeline":[...]}
     */
    private String emotionJson;

    /** 面试模式: text=文字模式, video=视频模式 */
    private String interviewMode;

    /**
     * 星系知识图谱数据 JSON，格式如：
     * [{"concept":"微服务架构","mastery":0.8,"category":"架构设计"},...]
     */
    private String knowledgeJson;

    private LocalDateTime createTime;

    private LocalDateTime endTime;
}
