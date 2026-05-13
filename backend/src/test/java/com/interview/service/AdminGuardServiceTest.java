package com.interview.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("AdminGuardService — 管理统计访问控制")
class AdminGuardServiceTest {

    @Test
    @DisplayName("开发者账号携带正确令牌时允许访问")
    void shouldAllowDeveloperWithValidToken() {
        DeveloperAccessService developerAccessService = mock(DeveloperAccessService.class);
        RequestUserResolver requestUserResolver = mock(RequestUserResolver.class);
        AdminGuardService service = new AdminGuardService(developerAccessService, requestUserResolver);
        ReflectionTestUtils.setField(service, "adminToken", "test-admin-token");

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/analytics/summary");
        request.addHeader("X-Admin-Token", "test-admin-token");
        when(requestUserResolver.resolveUserId(request)).thenReturn(1L);
        when(developerAccessService.isDeveloper(1L)).thenReturn(true);

        assertThatCode(() -> service.requireAdmin(request)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("普通账号即使携带正确令牌也不能访问管理数据")
    void shouldRejectNonDeveloperEvenWithValidToken() {
        DeveloperAccessService developerAccessService = mock(DeveloperAccessService.class);
        RequestUserResolver requestUserResolver = mock(RequestUserResolver.class);
        AdminGuardService service = new AdminGuardService(developerAccessService, requestUserResolver);
        ReflectionTestUtils.setField(service, "adminToken", "test-admin-token");

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/analytics/summary");
        request.addHeader("X-Admin-Token", "test-admin-token");
        when(requestUserResolver.resolveUserId(request)).thenReturn(2L);
        when(developerAccessService.isDeveloper(2L)).thenReturn(false);

        assertThatThrownBy(() -> service.requireAdmin(request))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("无权访问管理数据");
    }

    @Test
    @DisplayName("开发者账号携带错误令牌时仍然拒绝访问")
    void shouldRejectDeveloperWithInvalidToken() {
        DeveloperAccessService developerAccessService = mock(DeveloperAccessService.class);
        RequestUserResolver requestUserResolver = mock(RequestUserResolver.class);
        AdminGuardService service = new AdminGuardService(developerAccessService, requestUserResolver);
        ReflectionTestUtils.setField(service, "adminToken", "test-admin-token");

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/analytics/summary");
        request.addHeader("X-Admin-Token", "wrong-token");
        when(requestUserResolver.resolveUserId(request)).thenReturn(1L);
        when(developerAccessService.isDeveloper(1L)).thenReturn(true);

        assertThatThrownBy(() -> service.requireAdmin(request))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("无权访问管理数据");
    }
}
