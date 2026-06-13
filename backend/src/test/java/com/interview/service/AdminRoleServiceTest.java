package com.interview.service;

import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.interview.entity.User;
import com.interview.mapper.UserMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("AdminRoleService — 管理员角色管理")
class AdminRoleServiceTest {

    private UserMapper userMapper;
    private AdminRoleService service;

    @BeforeEach
    void setUp() {
        userMapper = mock(UserMapper.class);
        service = new AdminRoleService(userMapper);
    }

    @Test
    @DisplayName("只有 role=ADMIN 的用户被视为管理员")
    void shouldRecognizeAdminRole() {
        User admin = user(1L, "ADMIN");
        when(userMapper.selectById(1L)).thenReturn(admin);

        assertThat(service.isAdmin(1L)).isTrue();
        assertThat(service.isAdmin(null)).isFalse();
    }

    @Test
    @DisplayName("普通用户不能通过管理员校验")
    void shouldRejectUserRole() {
        when(userMapper.selectById(2L)).thenReturn(user(2L, "USER"));

        assertThatThrownBy(() -> service.requireAdmin(2L))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("无权访问管理数据");
    }

    @Test
    @DisplayName("管理员可以授予其他用户 ADMIN 角色")
    void shouldGrantAdminRole() {
        User operator = user(1L, "ADMIN");
        User target = user(2L, "USER");
        when(userMapper.selectById(1L)).thenReturn(operator);
        when(userMapper.selectById(2L)).thenReturn(target);

        User updated = service.grantAdmin(2L, 1L);

        assertThat(updated.getRole()).isEqualTo("ADMIN");
        assertThat(updated.getAdminGrantedBy()).isEqualTo(1L);
        assertThat(updated.getAdminGrantedAt()).isNotNull();
        verify(userMapper).updateById(target);
    }

    @Test
    @DisplayName("撤销管理员时必须至少保留一个 ADMIN")
    void shouldKeepAtLeastOneAdmin() {
        User operator = user(1L, "ADMIN");
        when(userMapper.selectById(1L)).thenReturn(operator);
        when(userMapper.update(isNull(), any())).thenReturn(0);

        assertThatThrownBy(() -> service.revokeAdmin(1L, 1L))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("至少保留一个管理员");
        verify(userMapper).update(isNull(), any());
    }

    @Test
    @DisplayName("存在其他管理员时可以撤销目标用户 ADMIN 角色")
    void shouldRevokeAdminRoleWhenAnotherAdminRemains() {
        User operator = user(1L, "ADMIN");
        User target = user(2L, "ADMIN");
        when(userMapper.selectById(1L)).thenReturn(operator);
        when(userMapper.selectById(2L)).thenReturn(target);
        when(userMapper.update(isNull(), any())).thenReturn(1);

        User updated = service.revokeAdmin(2L, 1L);

        assertThat(updated.getRole()).isEqualTo("USER");
        assertThat(updated.getAdminGrantedBy()).isNull();
        assertThat(updated.getAdminGrantedAt()).isNull();
        verify(userMapper).update(isNull(), any());
    }

    @Test
    @DisplayName("撤销 ADMIN 使用条件更新，避免并发下删光管理员")
    void shouldUseConditionalUpdateWhenRevokingAdminRole() {
        User operator = user(1L, "ADMIN");
        User target = user(2L, "ADMIN");
        when(userMapper.selectById(1L)).thenReturn(operator);
        when(userMapper.selectById(2L)).thenReturn(target);
        when(userMapper.update(isNull(), any())).thenReturn(0);

        assertThatThrownBy(() -> service.revokeAdmin(2L, 1L))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("至少保留一个管理员");

        ArgumentCaptor<UpdateWrapper<User>> update = updateWrapperCaptor();
        verify(userMapper).update(isNull(), update.capture());
        assertThat(update.getValue().getSqlSegment()).contains("id", "role");
        assertThat(update.getValue().getSqlSegment().toLowerCase()).contains("select count");
    }

    private User user(Long id, String role) {
        User user = new User();
        user.setId(id);
        user.setUsername("user" + id);
        user.setRole(role);
        return user;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static ArgumentCaptor<UpdateWrapper<User>> updateWrapperCaptor() {
        return ArgumentCaptor.forClass((Class) UpdateWrapper.class);
    }
}
