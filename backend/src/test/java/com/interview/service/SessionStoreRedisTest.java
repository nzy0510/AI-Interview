package com.interview.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("SessionStore — Agent 超时 Redis 状态")
class SessionStoreRedisTest {

    private static final String TIMEOUT_COUNT_KEY = "interview:agent_timeout_count:1";

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @Mock
    private ValueOperations<String, Object> valueOperations;

    private SessionStore store;

    @BeforeEach
    void setUp() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        store = new SessionStore(redisTemplate);
    }

    @Test
    @DisplayName("记录超时应使用 Redis 原子递增并刷新为会话 TTL")
    void shouldIncrementTimeoutCountInRedisWithSessionTtl() {
        when(valueOperations.increment(TIMEOUT_COUNT_KEY)).thenReturn(1L);

        assertThat(store.incrementAgentTimeoutCount(1L)).isEqualTo(1);

        verify(valueOperations).increment(TIMEOUT_COUNT_KEY);
        verify(redisTemplate).expire(TIMEOUT_COUNT_KEY, 2, TimeUnit.HOURS);
    }

    @Test
    @DisplayName("清除超时计数应同时删除 Redis 状态")
    void shouldClearTimeoutCountFromRedis() {
        store.clearAgentTimeoutCount(1L);

        verify(redisTemplate).delete(TIMEOUT_COUNT_KEY);
    }

    @Test
    @DisplayName("删除会话应同时删除 Redis 超时计数")
    void shouldDeleteTimeoutCountWithSession() {
        store.delete(1L);

        verify(redisTemplate).delete(TIMEOUT_COUNT_KEY);
    }
}
