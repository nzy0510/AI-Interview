package com.interview.service;

import com.interview.exception.RateLimitExceededException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("RateLimitService — 接口频率限制")
class RateLimitServiceTest {

    @Test
    @DisplayName("同一 IP 登录尝试超过窗口上限时返回限流异常")
    void shouldLimitFrequentLoginAttempts() {
        ClientFingerprintService fingerprintService = new ClientFingerprintService();
        RequestUserResolver userResolver = mock(RequestUserResolver.class);
        RateLimitService service = new RateLimitService(fingerprintService, userResolver);
        ReflectionTestUtils.setField(service, "enabled", true);
        ReflectionTestUtils.setField(fingerprintService, "hashSalt", "unit-test-salt");

        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/user/login");
        request.setRemoteAddr("127.0.0.1");
        when(userResolver.resolveUserId(request)).thenReturn(null);

        for (int i = 0; i < 20; i++) {
            service.check(request);
        }

        assertThatThrownBy(() -> service.check(request))
                .isInstanceOf(RateLimitExceededException.class)
                .hasMessageContaining("登录尝试过于频繁");
    }
}
