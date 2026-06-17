package com.interview.dto;

import lombok.Data;
import java.time.LocalDateTime;

/**
 * 岗位简历画像响应包装。
 * 包含画像数据和岗位元信息，前端据此了解画像所属岗位。
 */
@Data
public class ResumeProfileResponse {
    /** 画像 ID */
    private Long profileId;

    /** 结构化岗位 ID */
    private Long positionId;

    /** 当前岗位名称（最新查询时的名称，可能与上传时不同） */
    private String currentPositionName;

    /** 上传时的岗位名称快照 */
    private String uploadPositionSnapshot;

    /** 画像更新时间 */
    private LocalDateTime updatedAt;

    /** 画像分析结果 JSON（匹配度、技能、定制题等） */
    private Object analysis;
}
