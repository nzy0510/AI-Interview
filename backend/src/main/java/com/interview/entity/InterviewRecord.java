package com.interview.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("interview_record")
public class InterviewRecord {
    @TableId(type = IdType.AUTO)
    private Long id;
    
    private Long userId;
    
    private String position;
    
    private String chatHistory; // JSON string
    
    private Integer score;
    
    private String feedback;
    
    private LocalDateTime createTime;
    
    private LocalDateTime endTime;
}
