package com.interview.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.interview.common.Result;
import com.interview.entity.InterviewRecord;
import com.interview.mapper.InterviewRecordMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 面试历史控制器
 * 提供面试历史查询接口，用于前端 History.vue 的历史列表和能力成长曲线
 */
@RestController
@RequestMapping("/api/history")
@CrossOrigin
public class HistoryController {

    @Autowired
    private InterviewRecordMapper interviewRecordMapper;

    /**
     * 查询指定用户的所有面试历史记录（按时间倒序）
     * 注意：实际项目应从 JWT 中获取 userId，此处简化为固定用户
     */
    @GetMapping("/list")
    public Result<List<InterviewRecord>> listHistory(
            @RequestParam(value = "userId", defaultValue = "1") Long userId) {
        QueryWrapper<InterviewRecord> query = new QueryWrapper<>();
        query.eq("user_id", userId)
             .isNotNull("score")          // 只查已完成（有评分）的面试
             .orderByDesc("create_time")
             .last("LIMIT 50");           // 最多返回最近50场
        List<InterviewRecord> records = interviewRecordMapper.selectList(query);
        return Result.success(records);
    }

    /**
     * 查看单场面试的完整报告（供历史页面点击查看详情）
     */
    @GetMapping("/detail/{id}")
    public Result<InterviewRecord> detail(@PathVariable Long id) {
        InterviewRecord record = interviewRecordMapper.selectById(id);
        return Result.success(record);
    }
}
