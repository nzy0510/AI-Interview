package com.interview.service;

import com.alibaba.fastjson2.JSON;
import com.interview.entity.AppEventLog;
import com.interview.mapper.AppEventLogMapper;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
public class AppEventService {

    private final AppEventLogMapper eventLogMapper;
    private final ClientFingerprintService fingerprintService;
    private final RequestUserResolver userResolver;

    public AppEventService(AppEventLogMapper eventLogMapper,
                           ClientFingerprintService fingerprintService,
                           RequestUserResolver userResolver) {
        this.eventLogMapper = eventLogMapper;
        this.fingerprintService = fingerprintService;
        this.userResolver = userResolver;
    }

    public void recordRequest(HttpServletRequest request, int statusCode, Exception exception, long latencyMs) {
        String path = normalizePath(request.getRequestURI());
        String eventType = eventTypeForRequest(request.getMethod(), path);
        boolean success = exception == null && statusCode < 400;
        String errorMessage = exception != null ? exception.getMessage() : null;
        record(request, userResolver.resolveUserId(request), eventType, "api", path, request.getMethod(),
                statusCode, success, null, errorMessage, latencyMs, null);
    }

    public void recordProductEvent(HttpServletRequest request, Long userId, String eventType,
                                   String category, String path, Map<String, Object> metadata) {
        record(request, userId, normalizeEventType(eventType), safeCategory(category, "product"),
                truncate(path, 255), null, 200, true, null, null, null, metadata);
    }

    public void recordSystemEvent(Long userId, String eventType, String category,
                                  Map<String, Object> metadata, boolean success, String errorMessage) {
        AppEventLog logEntry = baseLog(null, userId);
        logEntry.setEventType(normalizeEventType(eventType));
        logEntry.setEventCategory(safeCategory(category, "system"));
        logEntry.setSuccess(success);
        logEntry.setErrorMessage(safeErrorMessage(errorMessage));
        logEntry.setMetadataJson(toMetadataJson(metadata));
        insertSafely(logEntry);
    }

    private void record(HttpServletRequest request, Long userId, String eventType, String category,
                        String path, String method, Integer statusCode, boolean success,
                        String errorCode, String errorMessage, Long latencyMs, Map<String, Object> metadata) {
        AppEventLog logEntry = baseLog(request, userId);
        logEntry.setEventType(normalizeEventType(eventType));
        logEntry.setEventCategory(safeCategory(category, "api"));
        logEntry.setPath(truncate(path, 255));
        logEntry.setHttpMethod(truncate(method, 16));
        logEntry.setStatusCode(statusCode);
        logEntry.setSuccess(success);
        logEntry.setErrorCode(truncate(errorCode, 64));
        logEntry.setErrorMessage(safeErrorMessage(errorMessage));
        logEntry.setLatencyMs(latencyMs);
        logEntry.setMetadataJson(toMetadataJson(metadata));
        insertSafely(logEntry);
    }

    private AppEventLog baseLog(HttpServletRequest request, Long userId) {
        AppEventLog logEntry = new AppEventLog();
        logEntry.setUserId(userId);
        logEntry.setCreateTime(LocalDateTime.now());
        logEntry.setRequestId(request != null ? requestId(request) : UUID.randomUUID().toString());
        if (request != null) {
            logEntry.setAnonymousId(fingerprintService.anonymousId(request));
            logEntry.setIpHash(fingerprintService.ipHash(request));
            logEntry.setUserAgentHash(fingerprintService.userAgentHash(request));
        }
        return logEntry;
    }

    private void insertSafely(AppEventLog logEntry) {
        try {
            eventLogMapper.insert(logEntry);
        } catch (Exception e) {
            log.trace("事件日志写入跳过: {}", e.getMessage());
        }
    }

    private String eventTypeForRequest(String method, String path) {
        if ("POST".equalsIgnoreCase(method) && "/api/user/login".equals(path)) return "LOGIN";
        if ("POST".equalsIgnoreCase(method) && "/api/user/register".equals(path)) return "REGISTER";
        if ("POST".equalsIgnoreCase(method) && "/api/user/send-code".equals(path)) return "VERIFICATION_CODE";
        if ("POST".equalsIgnoreCase(method) && "/api/user/forgot-password".equals(path)) return "PASSWORD_RESET_CODE";
        if ("POST".equalsIgnoreCase(method) && "/api/user/reset-password".equals(path)) return "PASSWORD_RESET";
        if ("POST".equalsIgnoreCase(method) && "/api/interview/start".equals(path)) return "INTERVIEW_START";
        if ("GET".equalsIgnoreCase(method) && "/api/interview/chatStream".equals(path)) return "INTERVIEW_CHAT";
        if ("POST".equalsIgnoreCase(method) && "/api/interview/finish".equals(path)) return "INTERVIEW_FINISH";
        if ("POST".equalsIgnoreCase(method) && "/api/resume/parse".equals(path)) return "RESUME_PARSE";
        if ("POST".equalsIgnoreCase(method) && "/api/user/mentor-insight/refresh".equals(path)) return "MENTOR_REFRESH";
        if ("GET".equalsIgnoreCase(method) && path.startsWith("/api/history/detail/")) return "REPORT_VIEW";
        if ("POST".equalsIgnoreCase(method) && "/api/feedback".equals(path)) return "FEEDBACK_SUBMIT";
        return "API_REQUEST";
    }

    private String normalizeEventType(String eventType) {
        if (eventType == null || eventType.isBlank()) return "UNKNOWN";
        String cleaned = eventType.trim().toUpperCase().replaceAll("[^A-Z0-9_:-]", "_");
        return truncate(cleaned, 64);
    }

    private String safeCategory(String category, String fallback) {
        if (category == null || category.isBlank()) return fallback;
        return truncate(category.trim().replaceAll("[^A-Za-z0-9_-]", "_"), 64);
    }

    private String normalizePath(String path) {
        if (path == null || path.isBlank()) return "/";
        return path.length() > 255 ? path.substring(0, 255) : path;
    }

    private String toMetadataJson(Map<String, Object> metadata) {
        if (metadata == null || metadata.isEmpty()) return null;
        String json = JSON.toJSONString(metadata);
        return json.length() > 2000 ? json.substring(0, 2000) : json;
    }

    private String requestId(HttpServletRequest request) {
        Object existing = request.getAttribute("requestId");
        if (existing instanceof String s && !s.isBlank()) return s;
        String id = UUID.randomUUID().toString();
        request.setAttribute("requestId", id);
        return id;
    }

    private String truncate(String value, int max) {
        if (value == null) return null;
        return value.length() <= max ? value : value.substring(0, max);
    }

    private String safeErrorMessage(String value) {
        if (value == null) return null;
        String redacted = value
                .replaceAll("sk-[A-Za-z0-9_-]{12,}", "sk-***")
                .replaceAll("Bearer\\s+[A-Za-z0-9._-]+", "Bearer ***")
                .replaceAll("eyJ[A-Za-z0-9._-]{20,}", "jwt-***");
        return truncate(redacted, 300);
    }
}
