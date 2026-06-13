package com.interview.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("AdminGuardService — 管理员角色访问控制")
class AdminGuardServiceTest {

    @Test
    @DisplayName("ADMIN 用户无需旧管理令牌也允许访问")
    void shouldAllowAdminUserWithoutLegacyToken() {
        AdminRoleService adminRoleService = mock(AdminRoleService.class);
        RequestUserResolver requestUserResolver = mock(RequestUserResolver.class);
        AdminGuardService service = new AdminGuardService(adminRoleService, requestUserResolver);

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/analytics/summary");
        when(requestUserResolver.resolveUserId(request)).thenReturn(1L);

        assertThatCode(() -> service.requireAdmin(request)).doesNotThrowAnyException();
        verify(adminRoleService).requireAdmin(1L);
    }

    @Test
    @DisplayName("非 ADMIN 用户即使携带旧令牌也不能访问管理数据")
    void shouldRejectNonAdminEvenWithLegacyToken() {
        AdminRoleService adminRoleService = mock(AdminRoleService.class);
        RequestUserResolver requestUserResolver = mock(RequestUserResolver.class);
        AdminGuardService service = new AdminGuardService(adminRoleService, requestUserResolver);

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/analytics/summary");
        request.addHeader("X-Admin-Token", "test-admin-token");
        when(requestUserResolver.resolveUserId(request)).thenReturn(2L);
        doThrow(new RuntimeException("无权访问管理数据")).when(adminRoleService).requireAdmin(2L);

        assertThatThrownBy(() -> service.requireAdmin(request))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("无权访问管理数据");
    }

    @Test
    @DisplayName("缺少当前用户身份时拒绝访问")
    void shouldRejectMissingCurrentUser() {
        AdminRoleService adminRoleService = mock(AdminRoleService.class);
        RequestUserResolver requestUserResolver = mock(RequestUserResolver.class);
        AdminGuardService service = new AdminGuardService(adminRoleService, requestUserResolver);

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/admin/question-bank/categories");
        when(requestUserResolver.resolveUserId(request)).thenReturn(null);
        doThrow(new RuntimeException("无权访问管理数据")).when(adminRoleService).requireAdmin(null);

        assertThatThrownBy(() -> service.requireAdmin(request))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("无权访问管理数据");
    }
}
