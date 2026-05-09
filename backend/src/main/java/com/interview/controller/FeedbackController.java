package com.interview.controller;

import com.interview.common.Result;
import com.interview.dto.FeedbackRequest;
import com.interview.entity.UserFeedback;
import com.interview.service.AdminGuardService;
import com.interview.service.FeedbackService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
public class FeedbackController {

    private final FeedbackService feedbackService;
    private final AdminGuardService adminGuardService;

    public FeedbackController(FeedbackService feedbackService, AdminGuardService adminGuardService) {
        this.feedbackService = feedbackService;
        this.adminGuardService = adminGuardService;
    }

    @PostMapping("/feedback")
    public Result<String> submit(@RequestBody @Valid FeedbackRequest requestBody,
                                 HttpServletRequest request) {
        feedbackService.submit(requestBody, request);
        return Result.success("反馈已收到");
    }

    @GetMapping("/admin/feedback")
    public Result<List<UserFeedback>> latest(@RequestParam(defaultValue = "20") int limit,
                                             HttpServletRequest request) {
        adminGuardService.requireAdmin(request);
        return Result.success(feedbackService.latest(limit));
    }
}
