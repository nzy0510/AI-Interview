package com.interview.service;

import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.interview.dto.McpTokenResponse;
import com.interview.dto.McpUsageResponse;
import com.interview.entity.McpAccessToken;
import com.interview.entity.McpDailyUsage;
import com.interview.mapper.McpAccessTokenMapper;
import com.interview.mapper.McpDailyUsageMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class McpTokenService {

    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    private static final String ACTIVE = "ACTIVE";
    private static final String READ = "READ";
    private static final String DEVELOPER = "DEVELOPER";

    private final SecureRandom secureRandom = new SecureRandom();
    private final McpAccessTokenMapper tokenMapper;
    private final McpDailyUsageMapper usageMapper;
    private final DeveloperAccessService developerAccessService;

    @Value("${app.mcp.public-url:}")
    private String configuredPublicUrl;

    @Value("${app.mcp.daily.total:150}")
    private int dailyTotalLimit;

    @Value("${app.mcp.daily.search:80}")
    private int dailySearchLimit;

    @Value("${app.mcp.daily.context:40}")
    private int dailyContextLimit;

    @Value("${app.mcp.daily.detail:60}")
    private int dailyDetailLimit;

    @Value("${app.mcp.daily.categories:30}")
    private int dailyCategoriesLimit;

    @Value("${app.mcp.daily.usage-status:30}")
    private int dailyUsageStatusLimit;

    @Value("${app.mcp.developer.daily.total:2000}")
    private int developerDailyTotalLimit;

    @Value("${app.mcp.developer.daily.search:1000}")
    private int developerDailySearchLimit;

    @Value("${app.mcp.developer.daily.context:500}")
    private int developerDailyContextLimit;

    @Value("${app.mcp.developer.daily.detail:500}")
    private int developerDailyDetailLimit;

    @Value("${app.mcp.developer.daily.categories:300}")
    private int developerDailyCategoriesLimit;

    @Value("${app.mcp.developer.daily.usage-status:300}")
    private int developerDailyUsageStatusLimit;

    public McpTokenService(McpAccessTokenMapper tokenMapper,
                           McpDailyUsageMapper usageMapper,
                           DeveloperAccessService developerAccessService) {
        this.tokenMapper = tokenMapper;
        this.usageMapper = usageMapper;
        this.developerAccessService = developerAccessService;
    }

    public McpTokenResponse status(Long userId, String requestOrigin) {
        McpAccessToken active = activeToken(userId);
        return buildResponse(userId, active, null, requestOrigin);
    }

    @Transactional
    public McpTokenResponse generate(Long userId, String requestOrigin) {
        tokenMapper.revokeActiveByUserId(userId);
        String token = newToken();
        McpAccessToken entity = new McpAccessToken();
        entity.setUserId(userId);
        entity.setTokenHash(sha256(token));
        entity.setTokenPrefix(prefix(token));
        entity.setRole(developerAccessService.isDeveloper(userId) ? DEVELOPER : READ);
        entity.setStatus(ACTIVE);
        tokenMapper.insert(entity);
        if (entity.getId() != null) {
            entity = tokenMapper.selectById(entity.getId());
        }
        return buildResponse(userId, entity, token, requestOrigin);
    }

    @Transactional
    public void revoke(Long userId) {
        tokenMapper.revokeActiveByUserId(userId);
    }

    public McpUsageResponse usage(Long userId) {
        McpUsageResponse response = new McpUsageResponse();
        LocalDate today = LocalDate.now(ZONE);
        response.setDate(today);
        Map<String, McpDailyUsage> usageByType = new LinkedHashMap<>();
        for (McpDailyUsage usage : usageMapper.selectUsageForDate(userId, today)) {
            usageByType.put(usage.getQuotaType(), usage);
        }
        for (var entry : limitsFor(userId).entrySet()) {
            McpDailyUsage usage = usageByType.get(entry.getKey());
            response.getItems().add(quotaItem(
                    entry.getKey(),
                    labelFor(entry.getKey()),
                    usage != null && usage.getUsedCount() != null ? usage.getUsedCount() : 0,
                    entry.getValue()
            ));
        }
        return response;
    }

    private McpTokenResponse buildResponse(Long userId, McpAccessToken active, String rawToken, String requestOrigin) {
        McpTokenResponse response = new McpTokenResponse();
        String endpoint = endpointUrl(requestOrigin);
        response.setEndpointUrl(endpoint);
        response.setHasActiveToken(active != null);
        response.setToken(rawToken);
        response.setQuotas(limitsFor(userId).entrySet().stream()
                .map(entry -> quotaItem(entry.getKey(), labelFor(entry.getKey()), 0, entry.getValue()))
                .toList());
        if (active != null) {
            response.setTokenPrefix(active.getTokenPrefix());
            response.setRole(active.getRole());
            response.setCreateTime(active.getCreateTime());
            response.setLastUsedAt(active.getLastUsedAt());
        }
        if (rawToken != null) {
            response.setClaudeCommand(claudeCommand(rawToken, endpoint));
            response.setJsonConfig(jsonConfig(rawToken, endpoint));
        }
        return response;
    }

    private McpAccessToken activeToken(Long userId) {
        return tokenMapper.selectOne(new LambdaQueryWrapper<McpAccessToken>()
                .eq(McpAccessToken::getUserId, userId)
                .eq(McpAccessToken::getStatus, ACTIVE)
                .orderByDesc(McpAccessToken::getCreateTime)
                .last("LIMIT 1"));
    }

    private Map<String, Integer> limitsFor(Long userId) {
        boolean developer = developerAccessService.isDeveloper(userId);
        Map<String, Integer> limits = new LinkedHashMap<>();
        limits.put("total", developer ? developerDailyTotalLimit : dailyTotalLimit);
        limits.put("search", developer ? developerDailySearchLimit : dailySearchLimit);
        limits.put("context", developer ? developerDailyContextLimit : dailyContextLimit);
        limits.put("detail", developer ? developerDailyDetailLimit : dailyDetailLimit);
        limits.put("categories", developer ? developerDailyCategoriesLimit : dailyCategoriesLimit);
        limits.put("usage_status", developer ? developerDailyUsageStatusLimit : dailyUsageStatusLimit);
        return limits;
    }

    private McpTokenResponse.QuotaItem quotaItem(String type, String label, int used, int limit) {
        McpTokenResponse.QuotaItem item = new McpTokenResponse.QuotaItem();
        item.setType(type);
        item.setLabel(label);
        item.setUsed(used);
        item.setLimit(limit);
        item.setRemaining(Math.max(0, limit - used));
        return item;
    }

    private String labelFor(String type) {
        return switch (type) {
            case "total" -> "每日 MCP 总调用";
            case "search" -> "每日题库检索";
            case "context" -> "每日上下文生成";
            case "detail" -> "每日题目摘要读取";
            case "categories" -> "每日分类查看";
            case "usage_status" -> "每日额度查询";
            default -> type;
        };
    }

    private String endpointUrl(String requestOrigin) {
        String configured = configuredPublicUrl == null ? "" : configuredPublicUrl.trim();
        if (!configured.isBlank()) {
            return configured;
        }
        String origin = requestOrigin == null ? "" : requestOrigin.trim();
        if (origin.isBlank()) {
            return "/mcp";
        }
        return origin.replaceAll("/+$", "") + "/mcp";
    }

    private String claudeCommand(String token, String endpoint) {
        return "claude mcp add --transport http --scope user interview-question-bank "
                + endpoint + " --header \"Authorization: Bearer " + token + "\"";
    }

    private String jsonConfig(String token, String endpoint) {
        Map<String, Object> config = Map.of(
                "mcpServers",
                Map.of(
                        "interview-question-bank",
                        Map.of(
                                "type", "http",
                                "url", endpoint,
                                "headers", Map.of("Authorization", "Bearer " + token)
                        )
                )
        );
        return JSON.toJSONString(config);
    }

    private String newToken() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return "iwmcp_" + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String prefix(String token) {
        return token.length() <= 18 ? token : token.substring(0, 18);
    }

    private String sha256(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(token.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder(hashed.length * 2);
            for (byte b : hashed) {
                builder.append(String.format("%02x", b));
            }
            return builder.toString();
        } catch (Exception e) {
            throw new IllegalStateException("无法生成 MCP token 摘要", e);
        }
    }
}
