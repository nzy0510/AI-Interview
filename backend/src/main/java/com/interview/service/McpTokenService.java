package com.interview.service;

import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.interview.dto.McpTokenResponse;
import com.interview.dto.McpUsageResponse;
import com.interview.entity.McpAccessToken;
import com.interview.entity.McpDailyUsage;
import com.interview.entity.McpQuotaPolicy;
import com.interview.mapper.McpAccessTokenMapper;
import com.interview.mapper.McpDailyUsageMapper;
import com.interview.mapper.McpQuotaPolicyMapper;
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
import java.util.List;
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
    private final McpQuotaPolicyMapper quotaPolicyMapper;
    private final DeveloperAccessService developerAccessService;

    @Value("${app.mcp.public-url:}")
    private String configuredPublicUrl;

    public McpTokenService(McpAccessTokenMapper tokenMapper,
                           McpDailyUsageMapper usageMapper,
                           McpQuotaPolicyMapper quotaPolicyMapper,
                           DeveloperAccessService developerAccessService) {
        this.tokenMapper = tokenMapper;
        this.usageMapper = usageMapper;
        this.quotaPolicyMapper = quotaPolicyMapper;
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
        McpAccessToken active = activeToken(userId);
        response.setDate(today);
        Map<String, McpDailyUsage> usageByType = new LinkedHashMap<>();
        for (McpDailyUsage usage : usageMapper.selectUsageForDate(userId, today)) {
            usageByType.put(usage.getQuotaType(), usage);
        }
        for (McpQuotaPolicy policy : quotaPoliciesFor(userId, active)) {
            McpDailyUsage usage = usageByType.get(policy.getQuotaType());
            response.getItems().add(quotaItem(
                    policy.getQuotaType(),
                    policy.getLabel(),
                    usage != null && usage.getUsedCount() != null ? usage.getUsedCount() : 0,
                    safeLimit(policy)
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
        response.setQuotas(quotaPoliciesFor(userId, active).stream()
                .map(policy -> quotaItem(policy.getQuotaType(), policy.getLabel(), 0, safeLimit(policy)))
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

    private List<McpQuotaPolicy> quotaPoliciesFor(Long userId, McpAccessToken active) {
        String roleName = quotaRole(userId, active);
        List<McpQuotaPolicy> policies = quotaPolicyMapper.selectByRole(roleName);
        return policies == null || policies.isEmpty() ? fallbackPolicies(roleName) : policies;
    }

    private String quotaRole(Long userId, McpAccessToken active) {
        if (active != null && DEVELOPER.equalsIgnoreCase(active.getRole())) {
            return DEVELOPER;
        }
        if (active != null) {
            return READ;
        }
        return developerAccessService.isDeveloper(userId) ? DEVELOPER : READ;
    }

    private List<McpQuotaPolicy> fallbackPolicies(String roleName) {
        int total = DEVELOPER.equals(roleName) ? 2000 : 150;
        int search = DEVELOPER.equals(roleName) ? 1000 : 80;
        int context = DEVELOPER.equals(roleName) ? 500 : 40;
        int detail = DEVELOPER.equals(roleName) ? 500 : 60;
        int categories = DEVELOPER.equals(roleName) ? 300 : 30;
        int usageStatus = DEVELOPER.equals(roleName) ? 300 : 30;
        return List.of(
                quotaPolicy(roleName, "total", "每日 MCP 总调用", total, 10),
                quotaPolicy(roleName, "search", "每日题库检索", search, 20),
                quotaPolicy(roleName, "context", "每日上下文生成", context, 30),
                quotaPolicy(roleName, "detail", "每日题目摘要读取", detail, 40),
                quotaPolicy(roleName, "categories", "每日分类查看", categories, 50),
                quotaPolicy(roleName, "usage_status", "每日额度查询", usageStatus, 60)
        );
    }

    private McpQuotaPolicy quotaPolicy(String roleName, String type, String label, int limit, int order) {
        McpQuotaPolicy policy = new McpQuotaPolicy();
        policy.setRoleName(roleName);
        policy.setQuotaType(type);
        policy.setLabel(label);
        policy.setLimitCount(limit);
        policy.setDisplayOrder(order);
        return policy;
    }

    private int safeLimit(McpQuotaPolicy policy) {
        return policy.getLimitCount() != null ? policy.getLimitCount() : 0;
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
