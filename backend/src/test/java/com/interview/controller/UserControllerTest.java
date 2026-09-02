package com.interview.controller;

import com.interview.common.Result;
import com.interview.dto.RegisterDTO;
import com.interview.dto.ResetPasswordDTO;
import com.interview.dto.MentorInsightResponse;
import com.interview.entity.User;
import com.interview.service.AdminRoleService;
import com.interview.service.AuthModeService;
import com.interview.service.DeveloperAccessService;
import com.interview.service.MentorService;
import com.interview.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class UserControllerTest {

    @Test
    void authConfigExposesLocalModeWithoutCredentials() {
        UserController controller = new UserController();
        AuthModeService authModeService = mock(AuthModeService.class);
        ReflectionTestUtils.setField(controller, "authModeService", authModeService);
        AuthModeService.PublicAuthConfig config =
                new AuthModeService.PublicAuthConfig("local-admin", false, false);
        when(authModeService.getPublicConfig()).thenReturn(config);

        Result<AuthModeService.PublicAuthConfig> result = controller.authConfig();

        assertThat(result.getData()).isEqualTo(config);
    }

    @Test
    void localModeBlocksAllEmailFlowsBeforeBusinessCalls() {
        UserController controller = new UserController();
        UserService userService = mock(UserService.class);
        AuthModeService authModeService = mock(AuthModeService.class);
        ReflectionTestUtils.setField(controller, "userService", userService);
        ReflectionTestUtils.setField(controller, "authModeService", authModeService);
        doThrow(new IllegalStateException("本地管理员模式已关闭邮箱注册与密码找回"))
                .when(authModeService).requireEmailAuthentication();

        assertThatThrownBy(() -> controller.register(new RegisterDTO()))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> controller.sendCode(Map.of("email", "local@example.com")))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> controller.forgotPassword(Map.of("email", "local@example.com")))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> controller.resetPassword(new ResetPasswordDTO()))
                .isInstanceOf(IllegalStateException.class);

        verify(authModeService, times(4)).requireEmailAuthentication();
        verifyNoInteractions(userService);
    }

    @Test
    void currentUserIncludesRoleAdminAndDeveloperFlags() {
        UserController controller = new UserController();
        UserService userService = mock(UserService.class);
        MentorService mentorService = mock(MentorService.class);
        DeveloperAccessService developerAccessService = mock(DeveloperAccessService.class);
        AdminRoleService adminRoleService = mock(AdminRoleService.class);
        ReflectionTestUtils.setField(controller, "userService", userService);
        ReflectionTestUtils.setField(controller, "mentorService", mentorService);
        ReflectionTestUtils.setField(controller, "developerAccessService", developerAccessService);
        ReflectionTestUtils.setField(controller, "adminRoleService", adminRoleService);

        User user = new User();
        user.setId(7L);
        user.setUsername("nzy333");
        user.setEmail("1525764737@qq.com");
        user.setRole("ADMIN");

        when(userService.getById(7L)).thenReturn(user);
        when(developerAccessService.isDeveloper(7L)).thenReturn(true);
        when(adminRoleService.isAdmin(7L)).thenReturn(true);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute("currentUserId", 7L);

        Result<Map<String, Object>> result = controller.getCurrentUser(request);

        assertThat(result.getData())
                .containsEntry("username", "nzy333")
                .containsEntry("role", "ADMIN")
                .containsEntry("isAdmin", true)
                .containsEntry("isDeveloper", true);
    }

    @Test
    void currentUserRejectsStaleTokenWhenUserNoLongerExists() {
        UserController controller = new UserController();
        UserService userService = mock(UserService.class);
        DeveloperAccessService developerAccessService = mock(DeveloperAccessService.class);
        AdminRoleService adminRoleService = mock(AdminRoleService.class);
        ReflectionTestUtils.setField(controller, "userService", userService);
        ReflectionTestUtils.setField(controller, "developerAccessService", developerAccessService);
        ReflectionTestUtils.setField(controller, "adminRoleService", adminRoleService);

        when(userService.getById(99L)).thenReturn(null);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute("currentUserId", 99L);

        assertThatThrownBy(() -> controller.getCurrentUser(request))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("登录已失效，请重新登录");
    }

    @Test
    void mentorInsightRequiresAndForwardsPositionId() {
        UserController controller = new UserController();
        MentorService mentorService = mock(MentorService.class);
        ReflectionTestUtils.setField(controller, "mentorService", mentorService);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute("currentUserId", 7L);
        MentorInsightResponse insight = new MentorInsightResponse();
        when(mentorService.getInsight(7L, 20L, false)).thenReturn(insight);

        Result<MentorInsightResponse> result = controller.mentorInsight(20L, request);

        assertThat(result.getData()).isSameAs(insight);
        verify(mentorService).getInsight(7L, 20L, false);
        assertThatThrownBy(() -> controller.mentorInsight(null, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("请选择岗位");
    }

    @Test
    void mentorInsightRejectsNonNumericPositionIdWithHttp400() throws Exception {
        UserController controller = new UserController();
        ReflectionTestUtils.setField(controller, "mentorService", mock(MentorService.class));
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new com.interview.config.GlobalExceptionHandler())
                .build();

        mockMvc.perform(get("/api/user/mentor-insight")
                        .param("positionId", "not-a-number")
                        .requestAttr("currentUserId", 7L))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    void refreshMentorInsightOnlyRefreshesRequestedPosition() {
        UserController controller = new UserController();
        MentorService mentorService = mock(MentorService.class);
        ReflectionTestUtils.setField(controller, "mentorService", mentorService);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute("currentUserId", 7L);

        controller.refreshMentorInsight(21L, request);

        verify(mentorService).getInsight(7L, 21L, true);
    }

    @Test
    void knowledgeCoverageRequiresAndForwardsPositionId() {
        UserController controller = new UserController();
        MentorService mentorService = mock(MentorService.class);
        ReflectionTestUtils.setField(controller, "mentorService", mentorService);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute("currentUserId", 7L);

        controller.knowledgeCoverage(request, 20L);

        verify(mentorService).getKnowledgeCoverageOnly(7L, 20L);
        assertThatThrownBy(() -> controller.knowledgeCoverage(request, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("请选择岗位");
    }
}
