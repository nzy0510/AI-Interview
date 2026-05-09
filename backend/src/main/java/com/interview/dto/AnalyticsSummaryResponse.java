package com.interview.dto;

import com.interview.entity.UserDailyUsage;
import com.interview.entity.UserFeedback;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Data
public class AnalyticsSummaryResponse {
    private Integer days;
    private Number pageViews;
    private Number uniqueVisitors;
    private Number registrations;
    private Number logins;
    private Number interviewStarts;
    private Number interviewFinishes;
    private Number resumeParses;
    private Number mentorGenerations;
    private Number feedbackCount;
    private Number errorCount;
    private Number limitedCount;
    private Double interviewCompletionRate;
    private List<Map<String, Object>> dailyEvents = new ArrayList<>();
    private List<Map<String, Object>> topPaths = new ArrayList<>();
    private List<UserDailyUsage> todayQuotaUsage = new ArrayList<>();
    private List<UserFeedback> latestFeedback = new ArrayList<>();
}
