package com.interview.service;

import com.interview.dto.QuotaStatusResponse;
import com.interview.entity.UserDailyUsage;
import com.interview.exception.QuotaExceededException;
import com.interview.mapper.UserDailyUsageMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
public class UsageQuotaService {

    public static final String INTERVIEW_START = "interview_start";
    public static final String AI_CHAT_TURN = "ai_chat_turn";
    public static final String RESUME_PARSE = "resume_parse";
    public static final String MENTOR_GENERATE = "mentor_generate";

    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    @Autowired(required = false)
    private StringRedisTemplate stringRedisTemplate;

    private final UserDailyUsageMapper usageMapper;
    private final DeveloperAccessService developerAccessService;
    private final Map<String, LocalCounter> localCounters = new ConcurrentHashMap<>();

    @Value("${app.quota.enabled:true}")
    private boolean quotaEnabled;

    @Value("${app.quota.daily.interview-start:5}")
    private int interviewStartLimit;

    @Value("${app.quota.daily.ai-chat-turn:80}")
    private int aiChatTurnLimit;

    @Value("${app.quota.daily.resume-parse:3}")
    private int resumeParseLimit;

    @Value("${app.quota.daily.mentor-generate:3}")
    private int mentorGenerateLimit;

    public UsageQuotaService(UserDailyUsageMapper usageMapper, DeveloperAccessService developerAccessService) {
        this.usageMapper = usageMapper;
        this.developerAccessService = developerAccessService;
    }

    public void consume(Long userId, String quotaType) {
        if (!quotaEnabled) return;
        if (userId == null) {
            throw new QuotaExceededException(quotaType, 0, "请先登录后再使用该功能");
        }
        if (developerAccessService.isDeveloper(userId)) {
            return;
        }
        int limit = limitFor(quotaType);
        if (limit <= 0) return;

        int used = increment(userId, quotaType);
        syncUsage(userId, quotaType, used, limit);
        if (used > limit) {
            throw new QuotaExceededException(quotaType, limit, exceededMessage(quotaType, limit));
        }
    }

    public QuotaStatusResponse getTodayStatus(Long userId) {
        LocalDate today = today();
        QuotaStatusResponse response = new QuotaStatusResponse();
        response.setDate(today);

        Map<String, Integer> usedByType = new LinkedHashMap<>();
        List<UserDailyUsage> rows = usageMapper.selectUsageForDate(userId, today);
        for (UserDailyUsage row : rows) {
            usedByType.put(row.getQuotaType(), row.getUsedCount());
        }

        for (String type : quotaDefinitions().keySet()) {
            int limit = limitFor(type);
            int used = Math.max(0, usedByType.getOrDefault(type, 0));
            response.getItems().add(new QuotaStatusResponse.Item(
                    type,
                    labelFor(type),
                    used,
                    limit,
                    Math.max(0, limit - used)
            ));
        }
        return response;
    }

    public Map<String, String> quotaDefinitions() {
        Map<String, String> definitions = new LinkedHashMap<>();
        definitions.put(INTERVIEW_START, "每日可开始面试次数");
        definitions.put(AI_CHAT_TURN, "每日 AI 对话轮次");
        definitions.put(RESUME_PARSE, "每日简历解析次数");
        definitions.put(MENTOR_GENERATE, "每日 AI Mentor 生成次数");
        return definitions;
    }

    private int increment(Long userId, String quotaType) {
        String key = redisKey(userId, quotaType);
        long ttlSeconds = secondsUntilTomorrow() + Duration.ofHours(2).toSeconds();
        if (stringRedisTemplate != null) {
            try {
                Long value = stringRedisTemplate.opsForValue().increment(key);
                stringRedisTemplate.expire(key, Duration.ofSeconds(ttlSeconds));
                return value != null ? value.intValue() : 1;
            } catch (Exception e) {
                log.trace("Redis 额度计数不可用，切换本地计数: {}", e.getMessage());
            }
        }

        LocalCounter counter = localCounters.compute(key, (k, existing) -> {
            long expiresAt = System.currentTimeMillis() + ttlSeconds * 1000;
            if (existing == null || existing.expiresAt < System.currentTimeMillis()) {
                return new LocalCounter(1, expiresAt);
            }
            existing.count += 1;
            return existing;
        });
        return counter.count;
    }

    private void syncUsage(Long userId, String quotaType, int used, int limit) {
        try {
            UserDailyUsage usage = new UserDailyUsage();
            usage.setUserId(userId);
            usage.setUsageDate(today());
            usage.setQuotaType(quotaType);
            usage.setUsedCount(used);
            usage.setLimitCount(limit);
            usageMapper.upsertUsage(usage);
        } catch (Exception e) {
            log.trace("额度使用快照写入失败: {}", e.getMessage());
        }
    }

    private int limitFor(String quotaType) {
        return switch (quotaType) {
            case INTERVIEW_START -> interviewStartLimit;
            case AI_CHAT_TURN -> aiChatTurnLimit;
            case RESUME_PARSE -> resumeParseLimit;
            case MENTOR_GENERATE -> mentorGenerateLimit;
            default -> 0;
        };
    }

    private String labelFor(String quotaType) {
        return quotaDefinitions().getOrDefault(quotaType, quotaType);
    }

    private String exceededMessage(String quotaType, int limit) {
        return switch (quotaType) {
            case INTERVIEW_START -> "今日可开始面试次数已用完，请明天再试";
            case AI_CHAT_TURN -> "今日 AI 对话额度已用完，请明天再试";
            case RESUME_PARSE -> "今日简历解析额度已用完，请明天再试";
            case MENTOR_GENERATE -> "今日 AI Mentor 分析额度已用完，请明天再试";
            default -> "今日使用额度已用完，请明天再试";
        };
    }

    private String redisKey(Long userId, String quotaType) {
        return "quota:" + today() + ":" + userId + ":" + quotaType;
    }

    private LocalDate today() {
        return LocalDate.now(ZONE);
    }

    private long secondsUntilTomorrow() {
        LocalDateTime now = LocalDateTime.now(ZONE);
        LocalDateTime tomorrow = LocalDateTime.of(now.toLocalDate().plusDays(1), LocalTime.MIN);
        return Duration.between(now, tomorrow).toSeconds();
    }

    private static class LocalCounter {
        int count;
        long expiresAt;

        LocalCounter(int count, long expiresAt) {
            this.count = count;
            this.expiresAt = expiresAt;
        }
    }
}
