package com.interview.config;

import com.interview.service.AppEventService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.UUID;

@Component
public class AuditLoggingInterceptor implements HandlerInterceptor {

    private static final String START_TIME_ATTR = "auditStartTimeMs";
    private final AppEventService appEventService;

    public AuditLoggingInterceptor(AppEventService appEventService) {
        this.appEventService = appEventService;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        request.setAttribute(START_TIME_ATTR, System.currentTimeMillis());
        request.setAttribute("requestId", UUID.randomUUID().toString());
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) return;
        Object start = request.getAttribute(START_TIME_ATTR);
        long latency = start instanceof Number n ? System.currentTimeMillis() - n.longValue() : 0L;
        appEventService.recordRequest(request, response.getStatus(), ex, latency);
    }
}
