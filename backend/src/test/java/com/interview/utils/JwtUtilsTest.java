package com.interview.utils;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("JwtUtils")
class JwtUtilsTest {

    private JwtUtils jwtUtils;

    @BeforeEach
    void setUp() {
        jwtUtils = new JwtUtils("test-sign-key-for-unit-tests-32bytes", 60_000);
    }

    @Test
    @DisplayName("用注入的密钥生成 Token，解析后应能取回原始 claims")
    void shouldGenerateAndParseTokenWithMatchingClaims() {
        Map<String, Object> inputClaims = Map.of("id", 42L, "username", "admin");
        String token = jwtUtils.generateJwt(inputClaims);

        Claims parsed = jwtUtils.parseJwt(token);

        assertThat(((Number) parsed.get("id")).longValue()).isEqualTo(42L);
        assertThat(parsed.get("username")).isEqualTo("admin");
    }

    @Test
    @DisplayName("用错误密钥解析 Token 应抛出异常")
    void shouldRejectTokenSignedWithDifferentKey() {
        String token = jwtUtils.generateJwt(Map.of("id", 1L));

        JwtUtils attackerUtils = new JwtUtils("a-different-secret-key-thats-wrong", 60_000);

        assertThatThrownBy(() -> attackerUtils.parseJwt(token))
                .isInstanceOf(Exception.class);
    }

    @Test
    @DisplayName("过期 Token 应被拒绝")
    void shouldRejectExpiredToken() {
        JwtUtils shortLived = new JwtUtils("this-is-a-test-key-at-least-32-bytes-long", 1);
        String token = shortLived.generateJwt(Map.of("id", 1L));

        try { Thread.sleep(5); } catch (InterruptedException ignored) {}

        assertThatThrownBy(() -> shortLived.parseJwt(token))
                .isInstanceOf(ExpiredJwtException.class);
    }
}
