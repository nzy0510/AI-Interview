package com.interview.controller;

import com.interview.common.Result;
import com.interview.dto.McpTokenResponse;
import com.interview.dto.McpUsageResponse;
import com.interview.service.McpTokenService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/mcp")
public class McpTokenController {

    private final McpTokenService mcpTokenService;

    public McpTokenController(McpTokenService mcpTokenService) {
        this.mcpTokenService = mcpTokenService;
    }

    @GetMapping("/token")
    public Result<McpTokenResponse> tokenStatus(HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("currentUserId");
        return Result.success(mcpTokenService.status(userId, origin(request)));
    }

    @PostMapping("/token")
    public Result<McpTokenResponse> generateToken(HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("currentUserId");
        return Result.success(mcpTokenService.generate(userId, origin(request)));
    }

    @DeleteMapping("/token")
    public Result<String> revokeToken(HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("currentUserId");
        mcpTokenService.revoke(userId);
        return Result.success("MCP token 已撤销");
    }

    @GetMapping("/usage")
    public Result<McpUsageResponse> usage(HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("currentUserId");
        return Result.success(mcpTokenService.usage(userId));
    }

    private String origin(HttpServletRequest request) {
        String forwardedProto = headerFirst(request, "X-Forwarded-Proto");
        String forwardedHost = headerFirst(request, "X-Forwarded-Host");
        if (forwardedHost == null || forwardedHost.isBlank()) {
            forwardedHost = request.getHeader("Host");
        }
        if (forwardedProto == null || forwardedProto.isBlank()) {
            forwardedProto = request.getScheme();
        }
        if (forwardedHost == null || forwardedHost.isBlank()) {
            return "";
        }
        return forwardedProto + "://" + forwardedHost;
    }

    private String headerFirst(HttpServletRequest request, String name) {
        String value = request.getHeader(name);
        if (value == null || value.isBlank()) {
            return "";
        }
        return value.split(",", 2)[0].trim();
    }
}
