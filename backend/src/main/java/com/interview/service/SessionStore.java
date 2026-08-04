package com.interview.service;

import com.alibaba.fastjson2.JSON;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * 面试会话存储：Redis 优先，不可用时自动降级到本地内存缓存。
 */
@Slf4j
public class SessionStore {

    private static final String CHAT_KEY_PREFIX = "interview:chat:";
    private static final String TAILORED_KEY_PREFIX = "interview:tailored:";
    private static final String USED_ATOMS_KEY_PREFIX = "interview:used_atoms:";
    private static final String AGENT_DISABLED_KEY_PREFIX = "interview:agent_disabled:";
    private static final String AGENT_TIMEOUT_COUNT_KEY_PREFIX = "interview:agent_timeout_count:";
    private static final long SESSION_TTL_HOURS = 2;

    private final RedisTemplate<String, Object> redisTemplate;
    private final Map<Long, List<ChatMessage>> localChatCache = new ConcurrentHashMap<>();
    private final Map<Long, List<String>> localTailoredCache = new ConcurrentHashMap<>();
    private final Map<Long, List<String>> localUsedAtomsCache = new ConcurrentHashMap<>();
    private final Map<Long, String> localAgentDisabledCache = new ConcurrentHashMap<>();
    private final Map<Long, Integer> localAgentTimeoutCountCache = new ConcurrentHashMap<>();
    private volatile boolean redisAvailable = true;

    public SessionStore(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    private boolean isRedisReady() {
        if (redisTemplate == null) return false;
        if (!redisAvailable) return false;
        try {
            redisTemplate.opsForValue().get("__ping__");
            return true;
        } catch (Exception e) {
            if (redisAvailable) {
                log.warn("Redis 不可用，已自动降级到内存缓存模式: {}", e.getMessage());
                redisAvailable = false;
            }
            return false;
        }
    }

    public void save(Long recordId, List<ChatMessage> messages) {
        localChatCache.put(recordId, new ArrayList<>(messages));
        if (isRedisReady()) {
            try {
                List<Map<String, String>> serialized = new ArrayList<>();
                for (ChatMessage msg : messages) {
                    Map<String, String> m = new HashMap<>();
                    if (msg instanceof UserMessage) {
                        m.put("type", "USER");
                        m.put("text", ((UserMessage) msg).singleText());
                    } else if (msg instanceof AiMessage) {
                        m.put("type", "AI");
                        m.put("text", ((AiMessage) msg).text());
                    } else if (msg instanceof SystemMessage) {
                        m.put("type", "SYSTEM");
                        m.put("text", ((SystemMessage) msg).text());
                    }
                    serialized.add(m);
                }
                redisTemplate.opsForValue().set(CHAT_KEY_PREFIX + recordId, JSON.toJSONString(serialized),
                        SESSION_TTL_HOURS, TimeUnit.HOURS);
            } catch (Exception e) {
                log.trace("Redis 写入跳过: {}", e.getMessage());
            }
        }
    }

    public List<ChatMessage> load(Long recordId) {
        if (isRedisReady()) {
            try {
                Object raw = redisTemplate.opsForValue().get(CHAT_KEY_PREFIX + recordId);
                if (raw != null) {
                    String json = raw instanceof String ? (String) raw : JSON.toJSONString(raw);
                    com.alibaba.fastjson2.JSONArray arr = JSON.parseArray(json);
                    List<ChatMessage> messages = new ArrayList<>();
                    for (int i = 0; i < arr.size(); i++) {
                        com.alibaba.fastjson2.JSONObject obj = arr.getJSONObject(i);
                        String type = obj.getString("type");
                        String text = obj.getString("text");
                        if (text == null) continue;
                        switch (type) {
                            case "USER" -> messages.add(new UserMessage(text));
                            case "AI" -> messages.add(new AiMessage(text));
                            case "SYSTEM" -> messages.add(new SystemMessage(text));
                        }
                    }
                    localChatCache.put(recordId, new ArrayList<>(messages));
                    return messages;
                }
            } catch (Exception e) {
                log.trace("Redis 读取跳过: {}", e.getMessage());
            }
        }
        List<ChatMessage> cached = localChatCache.get(recordId);
        return cached != null ? new ArrayList<>(cached) : null;
    }

    public void delete(Long recordId) {
        localChatCache.remove(recordId);
        localTailoredCache.remove(recordId);
        localUsedAtomsCache.remove(recordId);
        localAgentDisabledCache.remove(recordId);
        localAgentTimeoutCountCache.remove(recordId);
        if (isRedisReady()) {
            try {
                redisTemplate.delete(CHAT_KEY_PREFIX + recordId);
                redisTemplate.delete(TAILORED_KEY_PREFIX + recordId);
                redisTemplate.delete(USED_ATOMS_KEY_PREFIX + recordId);
                redisTemplate.delete(AGENT_DISABLED_KEY_PREFIX + recordId);
                redisTemplate.delete(AGENT_TIMEOUT_COUNT_KEY_PREFIX + recordId);
            } catch (Exception ignored) {}
        }
    }

    public void saveTailoredQuestions(Long recordId, List<String> questions) {
        localTailoredCache.put(recordId, questions);
        if (isRedisReady()) {
            try {
                redisTemplate.opsForValue().set(TAILORED_KEY_PREFIX + recordId, JSON.toJSONString(questions),
                        SESSION_TTL_HOURS, TimeUnit.HOURS);
            } catch (Exception ignored) {}
        }
    }

    public List<String> loadTailoredQuestions(Long recordId) {
        if (isRedisReady()) {
            try {
                Object raw = redisTemplate.opsForValue().get(TAILORED_KEY_PREFIX + recordId);
                if (raw != null) {
                    String json = raw instanceof String ? (String) raw : JSON.toJSONString(raw);
                    return JSON.parseArray(json, String.class);
                }
            } catch (Exception ignored) {}
        }
        return localTailoredCache.get(recordId);
    }

    /** 原子追加已用原子 ID，自动去重，线程安全 */
    public synchronized void addUsedAtoms(Long recordId, List<String> newIds) {
        List<String> current = localUsedAtomsCache.computeIfAbsent(recordId, k -> new ArrayList<>());
        for (String id : newIds) {
            if (!current.contains(id)) current.add(id);
        }
        if (isRedisReady()) {
            try {
                redisTemplate.opsForValue().set(USED_ATOMS_KEY_PREFIX + recordId, JSON.toJSONString(current),
                        SESSION_TTL_HOURS, TimeUnit.HOURS);
            } catch (Exception ignored) {}
        }
    }

    public void saveUsedAtoms(Long recordId, List<String> usedIds) {
        localUsedAtomsCache.put(recordId, new ArrayList<>(usedIds));
        if (isRedisReady()) {
            try {
                redisTemplate.opsForValue().set(USED_ATOMS_KEY_PREFIX + recordId, JSON.toJSONString(usedIds),
                        SESSION_TTL_HOURS, TimeUnit.HOURS);
            } catch (Exception ignored) {}
        }
    }

    public List<String> loadUsedAtoms(Long recordId) {
        if (isRedisReady()) {
            try {
                Object raw = redisTemplate.opsForValue().get(USED_ATOMS_KEY_PREFIX + recordId);
                if (raw != null) {
                    String json = raw instanceof String ? (String) raw : JSON.toJSONString(raw);
                    List<String> ids = JSON.parseArray(json, String.class);
                    localUsedAtomsCache.put(recordId, ids);
                    return ids;
                }
            } catch (Exception ignored) {}
        }
        List<String> cached = localUsedAtomsCache.get(recordId);
        return cached != null ? new ArrayList<>(cached) : new ArrayList<>();
    }

    public void disableAgent(Long recordId, String reasonCode) {
        String safeReason = reasonCode == null || reasonCode.isBlank() ? "AGENT_UNAVAILABLE" : reasonCode;
        localAgentDisabledCache.put(recordId, safeReason);
        if (isRedisReady()) {
            try {
                redisTemplate.opsForValue().set(AGENT_DISABLED_KEY_PREFIX + recordId, safeReason,
                        SESSION_TTL_HOURS, TimeUnit.HOURS);
            } catch (Exception e) {
                log.trace("Redis Agent 状态写入跳过: {}", e.getMessage());
            }
        }
    }

    public String loadAgentDisabledReason(Long recordId) {
        if (isRedisReady()) {
            try {
                Object raw = redisTemplate.opsForValue().get(AGENT_DISABLED_KEY_PREFIX + recordId);
                if (raw != null) {
                    String reason = String.valueOf(raw);
                    localAgentDisabledCache.put(recordId, reason);
                    return reason;
                }
            } catch (Exception e) {
                log.trace("Redis Agent 状态读取跳过: {}", e.getMessage());
            }
        }
        return localAgentDisabledCache.get(recordId);
    }

    public int incrementAgentTimeoutCount(Long recordId) {
        int timeoutCount = localAgentTimeoutCountCache.merge(recordId, 1, Integer::sum);
        if (isRedisReady()) {
            try {
                Long redisCount = redisTemplate.opsForValue()
                        .increment(AGENT_TIMEOUT_COUNT_KEY_PREFIX + recordId);
                if (redisCount != null) {
                    timeoutCount = redisCount.intValue();
                    localAgentTimeoutCountCache.put(recordId, timeoutCount);
                }
                redisTemplate.expire(AGENT_TIMEOUT_COUNT_KEY_PREFIX + recordId,
                        SESSION_TTL_HOURS, TimeUnit.HOURS);
            } catch (Exception e) {
                log.trace("Redis Agent 超时计数写入跳过");
            }
        }
        return timeoutCount;
    }

    public void clearAgentTimeoutCount(Long recordId) {
        localAgentTimeoutCountCache.remove(recordId);
        if (isRedisReady()) {
            try {
                redisTemplate.delete(AGENT_TIMEOUT_COUNT_KEY_PREFIX + recordId);
            } catch (Exception e) {
                log.trace("Redis Agent 超时计数清理跳过");
            }
        }
    }
}
