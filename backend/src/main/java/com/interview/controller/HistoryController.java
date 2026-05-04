package com.interview.controller;

import com.interview.common.Result;
import com.interview.entity.InterviewRecord;
import com.interview.service.InterviewService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import jakarta.servlet.http.HttpServletRequest;

import java.util.List;

/**
 * 面试历史控制器：委托 InterviewService 处理业务逻辑
 */
@RestController
@RequestMapping("/api/history")
public class HistoryController {

    @Autowired
    private InterviewService interviewService;

    @GetMapping("/list")
    public Result<List<InterviewRecord>> listHistory(HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("currentUserId");
        List<InterviewRecord> records = interviewService.getHistoryList(userId);
        return Result.success(records);
    }

    /**
     * 查看单场面试的完整报告
     */
    @GetMapping("/detail/{id}")
    public Result<InterviewRecord> detail(@PathVariable Long id, HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("currentUserId");
        InterviewRecord record = interviewService.getHistoryDetail(userId, id);
        return Result.success(record);
    }
}
