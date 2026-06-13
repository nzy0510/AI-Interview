package com.interview.controller;

import com.interview.config.GlobalExceptionHandler;
import com.interview.entity.User;
import com.interview.service.AdminGuardService;
import com.interview.service.AdminRoleService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
@DisplayName("AdminRoleController — 管理员角色接口")
class AdminRoleControllerTest {

    private MockMvc mockMvc;

    @Mock
    private AdminGuardService adminGuardService;

    @Mock
    private AdminRoleService adminRoleService;

    @BeforeEach
    void setUp() {
        AdminRoleController controller = new AdminRoleController(adminGuardService, adminRoleService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("ADMIN 用户可以授予其他用户 ADMIN 角色")
    void shouldGrantAdminRole() throws Exception {
        User updated = user(2L, "target", "ADMIN");
        when(adminGuardService.requireAdmin(any())).thenReturn(1L);
        when(adminRoleService.grantAdmin(2L, 1L)).thenReturn(updated);

        mockMvc.perform(post("/api/admin/users/2/admin"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(2))
                .andExpect(jsonPath("$.data.role").value("ADMIN"))
                .andExpect(jsonPath("$.data.isAdmin").value(true));

        verify(adminRoleService).grantAdmin(2L, 1L);
    }

    @Test
    @DisplayName("撤销最后一个 ADMIN 返回禁止访问错误")
    void shouldRejectRevokingLastAdmin() throws Exception {
        when(adminGuardService.requireAdmin(any())).thenReturn(1L);
        when(adminRoleService.revokeAdmin(1L, 1L))
                .thenThrow(new RuntimeException("至少保留一个管理员"));

        mockMvc.perform(delete("/api/admin/users/1/admin"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403))
                .andExpect(jsonPath("$.msg").value("至少保留一个管理员"));
    }

    private User user(Long id, String username, String role) {
        User user = new User();
        user.setId(id);
        user.setUsername(username);
        user.setRole(role);
        return user;
    }
}
