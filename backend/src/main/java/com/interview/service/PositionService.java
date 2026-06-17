package com.interview.service;

import com.interview.dto.VisiblePositionResponse;
import java.util.List;

public interface PositionService {

    /**
     * 获取当前用户可见的岗位摘要列表。
     * 包含公共岗位和用户自己的私有岗位，
     * 每个岗位附带历史记录数和简历画像状态。
     */
    List<VisiblePositionResponse> getVisiblePositions(Long userId);
}
