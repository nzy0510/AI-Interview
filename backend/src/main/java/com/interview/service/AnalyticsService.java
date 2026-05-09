package com.interview.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.interview.dto.AnalyticsSummaryResponse;
import com.interview.entity.UserDailyUsage;
import com.interview.entity.UserFeedback;
import com.interview.mapper.AppEventLogMapper;
import com.interview.mapper.UserDailyUsageMapper;
import com.interview.mapper.UserFeedbackMapper;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

@Service
public class AnalyticsService {

    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    private final AppEventLogMapper eventLogMapper;
    private final UserDailyUsageMapper usageMapper;
    private final UserFeedbackMapper feedbackMapper;

    public AnalyticsService(AppEventLogMapper eventLogMapper,
                            UserDailyUsageMapper usageMapper,
                            UserFeedbackMapper feedbackMapper) {
        this.eventLogMapper = eventLogMapper;
        this.usageMapper = usageMapper;
        this.feedbackMapper = feedbackMapper;
    }

    public AnalyticsSummaryResponse summary(int days) {
        int safeDays = Math.min(Math.max(days, 1), 90);
        Map<String, Object> raw = eventLogMapper.selectSummarySince(safeDays);

        AnalyticsSummaryResponse response = new AnalyticsSummaryResponse();
        response.setDays(safeDays);
        response.setPageViews(number(raw.get("pageViews")));
        response.setUniqueVisitors(number(raw.get("uniqueVisitors")));
        response.setRegistrations(number(raw.get("registrations")));
        response.setLogins(number(raw.get("logins")));
        response.setInterviewStarts(number(raw.get("interviewStarts")));
        response.setInterviewFinishes(number(raw.get("interviewFinishes")));
        response.setResumeParses(number(raw.get("resumeParses")));
        response.setMentorGenerations(number(raw.get("mentorGenerations")));
        response.setFeedbackCount(number(raw.get("feedbackCount")));
        response.setErrorCount(number(raw.get("errorCount")));
        response.setLimitedCount(number(raw.get("limitedCount")));

        double starts = response.getInterviewStarts().doubleValue();
        double finishes = response.getInterviewFinishes().doubleValue();
        response.setInterviewCompletionRate(starts > 0 ? Math.round(finishes * 10000.0 / starts) / 100.0 : 0.0);

        response.setDailyEvents(eventLogMapper.selectDailyEventCounts(safeDays));
        response.setTopPaths(eventLogMapper.selectTopPaths(safeDays));
        response.setTodayQuotaUsage(usageMapper.selectUsageSnapshot(LocalDate.now(ZONE), 200));
        response.setLatestFeedback(latestFeedback());
        return response;
    }

    private List<UserFeedback> latestFeedback() {
        return feedbackMapper.selectList(new LambdaQueryWrapper<UserFeedback>()
                .orderByDesc(UserFeedback::getCreateTime)
                .last("LIMIT 20"));
    }

    private Number number(Object value) {
        return value instanceof Number n ? n : 0;
    }
}
