package com.interview.controller;

import com.interview.common.Result;
import com.interview.dto.VisiblePositionResponse;
import com.interview.service.PositionService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 岗位控制器：提供正常用户页面的可见岗位摘要接口。
 * 与知识工作台 /api/knowledge-workspace/positions 分离，
 * 后者面向管理员维护，本接口面向普通用户的岗位选择器。
 */
@RestController
@RequestMapping("/api/positions")
public class PositionController {

    @Autowired
    private PositionService positionService;

    /**
     * 获取当前用户可见的岗位摘要列表（含历史记录数和简历画像状态）。
     * 用于历史页、简历画像页等正常用户页面的岗位选择器。
     */
    @GetMapping("/visible")
    public Result<List<VisiblePositionResponse>> listVisiblePositions(HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("currentUserId");
        List<VisiblePositionResponse> positions = positionService.getVisiblePositions(userId);
        return Result.success(positions);
    }
}
