package com.interview.service;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class AdminGuardService {

    private final DeveloperAccessService developerAccessService;
    private final RequestUserResolver requestUserResolver;

    @Value("${app.admin-token:}")
    private String adminToken;

    public AdminGuardService(DeveloperAccessService developerAccessService,
                             RequestUserResolver requestUserResolver) {
        this.developerAccessService = developerAccessService;
        this.requestUserResolver = requestUserResolver;
    }

    public void requireAdmin(HttpServletRequest request) {
        Long userId = requestUserResolver.resolveUserId(request);
        if (!developerAccessService.isDeveloper(userId)) {
            throw new RuntimeException("无权访问管理数据");
        }
        if (adminToken == null || adminToken.isBlank()) {
            throw new RuntimeException("无权访问管理数据：管理员令牌未配置，请先设置 APP_ADMIN_TOKEN");
        }
        String provided = request.getHeader("X-Admin-Token");
        if (provided == null || !constantTimeEquals(adminToken, provided)) {
            throw new RuntimeException("无权访问管理数据");
        }
    }

    private boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null) return false;
        int diff = a.length() ^ b.length();
        int max = Math.max(a.length(), b.length());
        for (int i = 0; i < max; i++) {
            char ca = i < a.length() ? a.charAt(i) : 0;
            char cb = i < b.length() ? b.charAt(i) : 0;
            diff |= ca ^ cb;
        }
        return diff == 0;
    }
}
