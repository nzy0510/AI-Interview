package com.interview.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.interview.dto.FeedbackRequest;
import com.interview.entity.UserFeedback;
import com.interview.mapper.UserFeedbackMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Service
public class FeedbackService {

    private final UserFeedbackMapper feedbackMapper;
    private final ClientFingerprintService fingerprintService;
    private final RequestUserResolver userResolver;
    private final AppEventService appEventService;

    public FeedbackService(UserFeedbackMapper feedbackMapper,
                           ClientFingerprintService fingerprintService,
                           RequestUserResolver userResolver,
                           AppEventService appEventService) {
        this.feedbackMapper = feedbackMapper;
        this.fingerprintService = fingerprintService;
        this.userResolver = userResolver;
        this.appEventService = appEventService;
    }

    public void submit(FeedbackRequest requestBody, HttpServletRequest request) {
        Long userId = userResolver.resolveUserId(request);

        UserFeedback feedback = new UserFeedback();
        feedback.setUserId(userId);
        feedback.setCategory(clean(requestBody.getCategory(), "general", 64));
        feedback.setContent(requestBody.getContent().trim());
        feedback.setContact(clean(requestBody.getContact(), null, 255));
        feedback.setPageUrl(clean(requestBody.getPageUrl(), null, 500));
        feedback.setStatus("OPEN");
        feedback.setIpHash(fingerprintService.ipHash(request));
        feedback.setUserAgentHash(fingerprintService.userAgentHash(request));
        feedback.setCreateTime(LocalDateTime.now());
        feedback.setUpdateTime(LocalDateTime.now());
        feedbackMapper.insert(feedback);

        appEventService.recordProductEvent(request, userId, "FEEDBACK_SUBMIT", "product",
                requestBody.getPageUrl(), Map.of("category", feedback.getCategory()));
    }

    public List<UserFeedback> latest(int limit) {
        return feedbackMapper.selectList(new LambdaQueryWrapper<UserFeedback>()
                .orderByDesc(UserFeedback::getCreateTime)
                .last("LIMIT " + Math.min(Math.max(limit, 1), 100)));
    }

    private String clean(String value, String fallback, int max) {
        if (value == null || value.isBlank()) return fallback;
        String trimmed = value.trim();
        return trimmed.length() <= max ? trimmed : trimmed.substring(0, max);
    }
}
