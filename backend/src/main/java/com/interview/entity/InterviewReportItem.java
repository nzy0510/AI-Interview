package com.interview.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("interview_report_item")
public class InterviewReportItem {
    @TableId(type = IdType.AUTO)
    private Long id;

    private Long reportId;
    private Long recordId;
    private Long turnId;
    private Integer itemIndex;
    private String phase;
    private String question;
    private String userAnswer;
    private BigDecimal score;
    private String referenceAnswer;
    private String improvementSuggestion;
    private String answerSource;
    private String matchedAtomSnapshotJson;
    private String modelProvider;
    private String modelName;
    private LocalDateTime generatedTime;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
