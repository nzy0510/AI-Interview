package com.interview.dto;

import lombok.Data;
import java.time.LocalDateTime;

/**
 * 用户可见岗位摘要响应（正常用户页面使用，非知识工作台维护数据）。
 * 包含岗位基本信息、历史记录数和简历画像状态。
 */
@Data
public class VisiblePositionResponse {
    private Long id;
    private String name;
    private String scope;
    private Long ownerUserId;
    private int historyCount;
    private boolean hasResumeProfile;
    private LocalDateTime resumeUpdatedAt;
}
