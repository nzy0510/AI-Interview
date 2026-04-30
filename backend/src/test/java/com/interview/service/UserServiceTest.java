package com.interview.service;

import com.interview.entity.User;
import com.interview.mapper.UserMapper;
import com.interview.service.impl.UserServiceImpl;
import com.interview.utils.JwtUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@DisplayName("UserService — 用户账号管理")
@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserMapper userMapper;

    @Mock
    private EmailService emailService;

    @Mock
    private JwtUtils jwtUtils;

    @InjectMocks
    private UserServiceImpl userService;

    @BeforeEach
    void setUp() {
        // ServiceImpl.getById() uses baseMapper, not the userMapper field
        ReflectionTestUtils.setField(userService, "baseMapper", userMapper);
    }

    private User createUser() {
        User u = new User();
        u.setId(1L);
        u.setUsername("admin");
        u.setPassword("e10adc3949ba59abbe56e057f20f883e"); // MD5 of 123456
        u.setEmail("admin@test.com");
        u.setNickname("管理员");
        return u;
    }

    @Test
    @DisplayName("更新用户昵称和邮箱")
    void shouldUpdateProfile() {
        User user = createUser();
        when(userMapper.selectById(1L)).thenReturn(user);
        when(userMapper.updateById(any(User.class))).thenReturn(1);

        userService.updateProfile(1L, "新昵称", "new@test.com");

        assertThat(user.getNickname()).isEqualTo("新昵称");
        assertThat(user.getEmail()).isEqualTo("new@test.com");
        verify(userMapper).updateById(user);
    }

    @Test
    @DisplayName("修改密码：旧密码正确时成功")
    void shouldChangePasswordWithCorrectOldPassword() {
        User user = createUser();
        when(userMapper.selectById(1L)).thenReturn(user);
        when(userMapper.updateById(any(User.class))).thenReturn(1);

        userService.changePassword(1L, "123456", "654321");

        verify(userMapper).updateById(user);
    }

    @Test
    @DisplayName("修改密码：旧密码错误时抛异常")
    void shouldThrowWhenOldPasswordWrong() {
        User user = createUser();
        when(userMapper.selectById(1L)).thenReturn(user);

        assertThatThrownBy(() -> userService.changePassword(1L, "wrong", "654321"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("旧密码错误");
    }
}
