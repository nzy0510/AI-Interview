package com.interview.controller;

import com.interview.common.Result;
import com.interview.entity.User;
import com.interview.service.AdminGuardService;
import com.interview.service.AdminRoleService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/users")
public class AdminRoleController {

    private final AdminGuardService adminGuardService;
    private final AdminRoleService adminRoleService;

    public AdminRoleController(AdminGuardService adminGuardService,
                               AdminRoleService adminRoleService) {
        this.adminGuardService = adminGuardService;
        this.adminRoleService = adminRoleService;
    }

    @PostMapping("/{userId}/admin")
    public Result<Map<String, Object>> grantAdmin(@PathVariable Long userId,
                                                  HttpServletRequest request) {
        Long operatorId = adminGuardService.requireAdmin(request);
        return Result.success(toAdminUser(adminRoleService.grantAdmin(userId, operatorId)));
    }

    @DeleteMapping("/{userId}/admin")
    public Result<Map<String, Object>> revokeAdmin(@PathVariable Long userId,
                                                   HttpServletRequest request) {
        Long operatorId = adminGuardService.requireAdmin(request);
        return Result.success(toAdminUser(adminRoleService.revokeAdmin(userId, operatorId)));
    }

    private Map<String, Object> toAdminUser(User user) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", user.getId());
        data.put("username", user.getUsername());
        data.put("nickname", user.getNickname() != null ? user.getNickname() : user.getUsername());
        data.put("email", user.getEmail() != null ? user.getEmail() : "");
        data.put("role", user.getRole());
        data.put("isAdmin", AdminRoleService.ROLE_ADMIN.equalsIgnoreCase(user.getRole()));
        return data;
    }
}
