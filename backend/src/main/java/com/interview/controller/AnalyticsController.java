package com.interview.controller;

import com.interview.common.Result;
import com.interview.dto.AnalyticsEventRequest;
import com.interview.dto.AnalyticsSummaryResponse;
import com.interview.dto.QuotaStatusResponse;
import com.interview.service.AdminGuardService;
import com.interview.service.AnalyticsService;
import com.interview.service.AppEventService;
import com.interview.service.RequestUserResolver;
import com.interview.service.UsageQuotaService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/analytics")
public class AnalyticsController {

    private final AppEventService appEventService;
    private final RequestUserResolver userResolver;
    private final UsageQuotaService usageQuotaService;
    private final AnalyticsService analyticsService;
    private final AdminGuardService adminGuardService;

    public AnalyticsController(AppEventService appEventService,
                               RequestUserResolver userResolver,
                               UsageQuotaService usageQuotaService,
                               AnalyticsService analyticsService,
                               AdminGuardService adminGuardService) {
        this.appEventService = appEventService;
        this.userResolver = userResolver;
        this.usageQuotaService = usageQuotaService;
        this.analyticsService = analyticsService;
        this.adminGuardService = adminGuardService;
    }

    @PostMapping("/event")
    public Result<String> track(@RequestBody @Valid AnalyticsEventRequest event,
                                HttpServletRequest request) {
        Long userId = userResolver.resolveUserId(request);
        appEventService.recordProductEvent(request, userId, event.getEventType(),
                event.getCategory(), event.getPageUrl(), event.getMetadata());
        return Result.success("ok");
    }

    @GetMapping("/quota/me")
    public Result<QuotaStatusResponse> myQuota(HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("currentUserId");
        return Result.success(usageQuotaService.getTodayStatus(userId));
    }

    @GetMapping("/summary")
    public Result<AnalyticsSummaryResponse> summary(@RequestParam(defaultValue = "7") int days,
                                                    HttpServletRequest request) {
        adminGuardService.requireAdmin(request);
        return Result.success(analyticsService.summary(days));
    }
}
