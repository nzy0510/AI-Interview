package com.interview.service;

import org.springframework.stereotype.Service;
import jakarta.servlet.http.HttpServletRequest;

@Service
public class AdminGuardService {

    private final AdminRoleService adminRoleService;
    private final RequestUserResolver requestUserResolver;

    public AdminGuardService(AdminRoleService adminRoleService,
                             RequestUserResolver requestUserResolver) {
        this.adminRoleService = adminRoleService;
        this.requestUserResolver = requestUserResolver;
    }

    public Long requireAdmin(HttpServletRequest request) {
        Long userId = requestUserResolver.resolveUserId(request);
        adminRoleService.requireAdmin(userId);
        return userId;
    }
}
