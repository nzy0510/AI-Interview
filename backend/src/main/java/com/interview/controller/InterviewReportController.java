package com.interview.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.interview.common.Result;
import com.interview.dto.report.InterviewReportItemResponse;
import com.interview.dto.report.InterviewReportResponse;
import com.interview.entity.InterviewRecord;
import com.interview.entity.InterviewReport;
import com.interview.entity.InterviewReportItem;
import com.interview.mapper.InterviewRecordMapper;
import com.interview.mapper.InterviewReportItemMapper;
import com.interview.mapper.InterviewReportMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/interview/reports")
public class InterviewReportController {

    private final InterviewRecordMapper recordMapper;
    private final InterviewReportMapper reportMapper;
    private final InterviewReportItemMapper reportItemMapper;

    public InterviewReportController(InterviewRecordMapper recordMapper,
                                     InterviewReportMapper reportMapper,
                                     InterviewReportItemMapper reportItemMapper) {
        this.recordMapper = recordMapper;
        this.reportMapper = reportMapper;
        this.reportItemMapper = reportItemMapper;
    }

    @GetMapping("/{recordId}")
    public Result<InterviewReportResponse> detail(@PathVariable Long recordId,
                                                  HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("currentUserId");
        InterviewRecord record = recordMapper.selectOne(new QueryWrapper<InterviewRecord>()
                .eq("id", recordId)
                .eq("user_id", userId)
                .last("LIMIT 1"));
        if (record == null) {
            throw new RuntimeException("面试记录不存在或无权访问");
        }
        InterviewReport report = reportMapper.selectOne(new QueryWrapper<InterviewReport>()
                .eq("record_id", recordId)
                .last("LIMIT 1"));
        if (report == null) {
            throw new RuntimeException("报告尚未创建");
        }
        List<InterviewReportItemResponse> items = reportItemMapper.selectList(new QueryWrapper<InterviewReportItem>()
                        .eq("report_id", report.getId())
                        .orderByAsc("item_index"))
                .stream()
                .map(InterviewReportItemResponse::from)
                .toList();
        return Result.success(InterviewReportResponse.from(report, items));
    }
}
