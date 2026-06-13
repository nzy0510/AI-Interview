package com.interview.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("interview_report")
public class InterviewReport {
    @TableId(type = IdType.AUTO)
    private Long id;

    private Long recordId;
    private Long userId;
    private Long positionId;
    private Long jobId;
    private String status;
    private Integer overallScore;
    private String summary;
    private String abilityJson;
    private String recommendationJson;
    private String errorMessage;
    private String modelProvider;
    private String modelName;
    private LocalDateTime generatedAt;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
