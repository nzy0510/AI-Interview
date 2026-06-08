package com.interview.config;

import com.interview.utils.JwtUtils;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("JwtInterceptor")
class JwtInterceptorTest {

    private final JwtUtils jwtUtils = new JwtUtils("test-sign-key-for-unit-tests-32bytes", 60_000);
    private final JwtInterceptor interceptor = new JwtInterceptor(jwtUtils);

    @Test
    @DisplayName("普通 API 不应接受 URL query token")
    void shouldRejectQueryTokenForNonSseApi() throws Exception {
        String token = jwtUtils.generateJwt(Map.of("id", 7L));
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/user/me");
        request.setParameter("token", token);
        MockHttpServletResponse response = new MockHttpServletResponse();

        boolean allowed = interceptor.preHandle(request, response, new Object());

        assertThat(allowed).isFalse();
        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(request.getAttribute("currentUserId")).isNull();
    }

    @Test
    @DisplayName("SSE 面试流接口可以兼容 URL query token")
    void shouldAcceptQueryTokenForSseChatStream() throws Exception {
        String token = jwtUtils.generateJwt(Map.of("id", 7L));
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/interview/chatStream");
        request.setParameter("token", token);
        MockHttpServletResponse response = new MockHttpServletResponse();

        boolean allowed = interceptor.preHandle(request, response, new Object());

        assertThat(allowed).isTrue();
        assertThat(request.getAttribute("currentUserId")).isEqualTo(7L);
    }
}
