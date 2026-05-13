package com.interview.service;

import com.interview.exception.RateLimitExceededException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Slf4j
@Service
public class RateLimitService {

    @Autowired(required = false)
    private StringRedisTemplate stringRedisTemplate;

    private final ClientFingerprintService fingerprintService;
    private final RequestUserResolver userResolver;
    private final DeveloperAccessService developerAccessService;
    private final ConcurrentMap<String, LocalCounter> localCounters = new ConcurrentHashMap<>();

    @Value("${app.rate-limit.enabled:true}")
    private boolean enabled;

    public RateLimitService(ClientFingerprintService fingerprintService,
                            RequestUserResolver userResolver,
                            DeveloperAccessService developerAccessService) {
        this.fingerprintService = fingerprintService;
        this.userResolver = userResolver;
        this.developerAccessService = developerAccessService;
    }

    public void check(HttpServletRequest request) {
        if (!enabled || "OPTIONS".equalsIgnoreCase(request.getMethod())) return;

        String ipSubject = "ip:" + fingerprintService.ipHash(request);
        apply("global-api", ipSubject, 300, Duration.ofMinutes(1), "访问过于频繁，请稍后再试");

        String path = request.getRequestURI();
        String method = request.getMethod();
        Long userId = userResolver.resolveUserId(request);
        String actor = userId != null ? "u:" + userId + ":ip:" + fingerprintService.ipHash(request) : ipSubject;
        boolean developer = developerAccessService.isDeveloper(userId);

        if (is(method, path, "POST", "/api/user/login")) {
            apply("login", ipSubject, 20, Duration.ofMinutes(10), "登录尝试过于频繁，请稍后再试");
        } else if (is(method, path, "POST", "/api/user/register")) {
            apply("register", ipSubject, 10, Duration.ofHours(1), "注册请求过于频繁，请稍后再试");
        } else if (is(method, path, "POST", "/api/user/send-code")
                || is(method, path, "POST", "/api/user/forgot-password")) {
            apply("email-code", ipSubject, 5, Duration.ofHours(1), "验证码请求过于频繁，请稍后再试");
        } else if (is(method, path, "POST", "/api/user/reset-password")) {
            apply("password-reset", ipSubject, 10, Duration.ofHours(1), "密码重置请求过于频繁，请稍后再试");
        } else if (is(method, path, "POST", "/api/interview/start")) {
            applyForUser("interview-start", actor, developer, 20, Duration.ofMinutes(10), "开始面试过于频繁，请稍后再试");
        } else if (is(method, path, "GET", "/api/interview/chatStream")) {
            applyForUser("interview-chat", actor, developer, 80, Duration.ofMinutes(10), "AI 对话请求过于频繁，请稍后再试");
        } else if (is(method, path, "POST", "/api/interview/finish")) {
            applyForUser("interview-finish", actor, developer, 10, Duration.ofMinutes(30), "报告生成请求过于频繁，请稍后再试");
        } else if (is(method, path, "POST", "/api/resume/parse")) {
            applyForUser("resume-parse", actor, developer, 10, Duration.ofHours(1), "简历解析请求过于频繁，请稍后再试");
        } else if (is(method, path, "POST", "/api/user/mentor-insight/refresh")) {
            applyForUser("mentor-refresh", actor, developer, 10, Duration.ofHours(1), "AI Mentor 刷新过于频繁，请稍后再试");
        } else if (is(method, path, "POST", "/api/feedback")) {
            applyForUser("feedback", actor, developer, 10, Duration.ofHours(1), "反馈提交过于频繁，请稍后再试");
        } else if (path.startsWith("/mcp")) {
            apply("mcp", ipSubject, 120, Duration.ofMinutes(1), "MCP 请求过于频繁，请稍后再试");
        }
    }

    private boolean is(String actualMethod, String actualPath, String method, String path) {
        return method.equalsIgnoreCase(actualMethod) && path.equals(actualPath);
    }

    private void apply(String rule, String subject, int limit, Duration window, String message) {
        if (limit <= 0) return;
        long count = increment(rule, subject, window);
        if (count > limit) {
            throw new RateLimitExceededException(message, window.toSeconds());
        }
    }

    private void applyForUser(String rule, String subject, boolean developer, int limit, Duration window, String message) {
        if (developer) {
            return;
        }
        apply(rule, subject, limit, window, message);
    }

    private long increment(String rule, String subject, Duration window) {
        long nowSeconds = System.currentTimeMillis() / 1000;
        long bucket = nowSeconds / window.toSeconds();
        String key = "rate:" + rule + ":" + subject + ":" + bucket;
        if (stringRedisTemplate != null) {
            try {
                Long value = stringRedisTemplate.opsForValue().increment(key);
                stringRedisTemplate.expire(key, window.plusSeconds(15));
                return value != null ? value : 1L;
            } catch (Exception e) {
                log.trace("Redis 限流计数不可用，切换本地计数: {}", e.getMessage());
            }
        }

        long expiresAt = System.currentTimeMillis() + window.plusSeconds(15).toMillis();
        LocalCounter counter = localCounters.compute(key, (k, existing) -> {
            if (existing == null || existing.expiresAt < System.currentTimeMillis()) {
                return new LocalCounter(1, expiresAt);
            }
            existing.count += 1;
            return existing;
        });
        return counter.count;
    }

    private static class LocalCounter {
        long count;
        long expiresAt;

        LocalCounter(long count, long expiresAt) {
            this.count = count;
            this.expiresAt = expiresAt;
        }
    }
}
