package com.interview.service;

import com.interview.entity.User;
import com.interview.entity.UserPreference;
import com.interview.mapper.UserMapper;
import com.interview.mapper.UserPreferenceMapper;
import com.interview.service.impl.UserServiceImpl;
import com.interview.utils.JwtUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;

@DisplayName("UserService — 用户账号管理")
@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserMapper userMapper;

    @Mock
    private UserPreferenceMapper prefMapper;

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

    @Test
    @DisplayName("保存偏好：更新时应忽略客户端传入的 userId 并保留未提交字段")
    void shouldIgnoreClientUserIdWhenUpdatingPreference() {
        UserPreference existing = new UserPreference();
        existing.setId(7L);
        existing.setUserId(1L);
        existing.setDefaultMode("text");
        existing.setDefaultRole("Java 后端开发");
        existing.setFocusAreas("[\"projects\"]");
        existing.setDifficultyLevel("mid");

        UserPreference input = new UserPreference();
        input.setId(999L);
        input.setUserId(42L);
        input.setDefaultMode("video");
        input.setDefaultRole("Web 前端开发");
        input.setDifficultyLevel("senior");

        when(prefMapper.selectOne(any())).thenReturn(existing);
        when(prefMapper.updateById(any(UserPreference.class))).thenReturn(1);

        userService.updatePreference(1L, input);

        ArgumentCaptor<UserPreference> captor = ArgumentCaptor.forClass(UserPreference.class);
        verify(prefMapper).updateById(captor.capture());
        UserPreference saved = captor.getValue();
        assertThat(saved.getId()).isEqualTo(7L);
        assertThat(saved.getUserId()).isEqualTo(1L);
        assertThat(saved.getDefaultMode()).isEqualTo("video");
        assertThat(saved.getDefaultRole()).isEqualTo("Web 前端开发");
        assertThat(saved.getFocusAreas()).isEqualTo("[\"projects\"]");
        assertThat(saved.getDifficultyLevel()).isEqualTo("senior");
    }

    @Test
    @DisplayName("保存偏好：新增时应白名单复制字段并回退非法枚举")
    void shouldWhitelistPreferenceFieldsOnInsert() {
        UserPreference input = new UserPreference();
        input.setId(999L);
        input.setUserId(42L);
        input.setDefaultMode("admin");
        input.setDefaultRole("Java 后端开发");
        input.setDifficultyLevel("root");

        when(prefMapper.selectOne(any())).thenReturn(null);
        when(prefMapper.insert(any(UserPreference.class))).thenReturn(1);

        userService.updatePreference(1L, input);

        ArgumentCaptor<UserPreference> captor = ArgumentCaptor.forClass(UserPreference.class);
        verify(prefMapper).insert(captor.capture());
        UserPreference saved = captor.getValue();
        assertThat(saved.getId()).isNull();
        assertThat(saved.getUserId()).isEqualTo(1L);
        assertThat(saved.getDefaultMode()).isEqualTo("text");
        assertThat(saved.getDefaultRole()).isEqualTo("Java 后端开发");
        assertThat(saved.getDifficultyLevel()).isEqualTo("mid");
    }

    @Test
    @DisplayName("更新用户头像链接")
    void shouldUpdateAvatar() {
        User user = createUser();
        when(userMapper.selectById(1L)).thenReturn(user);
        when(userMapper.updateById(any(User.class))).thenReturn(1);

        userService.updateAvatar(1L, "/uploads/avatars/1_abc123.png");

        assertThat(user.getAvatar()).isEqualTo("/uploads/avatars/1_abc123.png");
        verify(userMapper).updateById(user);
    }

    @Test
    @DisplayName("更新头像：用户不存在时抛异常")
    void shouldThrowWhenUserNotFoundForAvatar() {
        when(userMapper.selectById(999L)).thenReturn(null);

        assertThatThrownBy(() -> userService.updateAvatar(999L, "/uploads/avatars/x.png"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("用户不存在");
    }

    @Test
    @DisplayName("上传头像：文件类型不合法时抛异常")
    void shouldRejectInvalidAvatarType() {
        User user = createUser();
        when(userMapper.selectById(1L)).thenReturn(user);
        org.springframework.web.multipart.MultipartFile file = mock(org.springframework.web.multipart.MultipartFile.class);
        when(file.getContentType()).thenReturn("image/gif");

        assertThatThrownBy(() -> userService.uploadAvatar(1L, file))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("仅支持");

        verify(userMapper, never()).updateById(any());
    }

    @Test
    @DisplayName("上传头像：文件过大时抛异常")
    void shouldRejectOversizedAvatar() {
        User user = createUser();
        when(userMapper.selectById(1L)).thenReturn(user);
        org.springframework.web.multipart.MultipartFile file = mock(org.springframework.web.multipart.MultipartFile.class);
        when(file.getContentType()).thenReturn("image/png");
        when(file.getSize()).thenReturn(5 * 1024 * 1024L);

        assertThatThrownBy(() -> userService.uploadAvatar(1L, file))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("2MB");

        verify(userMapper, never()).updateById(any());
    }
}
