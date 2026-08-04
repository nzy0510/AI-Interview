package com.interview.service;

import cn.hutool.crypto.digest.BCrypt;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.interview.config.AuthModeProperties;
import com.interview.entity.User;
import com.interview.mapper.UserMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("LocalAdminInitializer — 本地管理员初始化")
class LocalAdminInitializerTest {

    @Test
    @DisplayName("空库在本地模式创建 BCrypt 管理员")
    void shouldCreateLocalAdminWhenMissing() throws Exception {
        UserMapper userMapper = mock(UserMapper.class);
        AuthModeProperties properties = localAdminProperties();
        when(userMapper.selectOne(any(Wrapper.class))).thenReturn(null);
        when(userMapper.insert(any(User.class))).thenReturn(1);
        LocalAdminInitializer initializer = new LocalAdminInitializer(userMapper, properties);

        initializer.run(null);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userMapper).insert(captor.capture());
        User saved = captor.getValue();
        assertThat(saved.getUsername()).isEqualTo("admin");
        assertThat(saved.getEmail()).isNull();
        assertThat(saved.getRole()).isEqualTo("ADMIN");
        assertThat(saved.getAdminGrantedAt()).isNotNull();
        assertThat(BCrypt.checkpw("admin123", saved.getPassword())).isTrue();
    }

    @Test
    @DisplayName("已有同名账号时不覆盖密码或资料")
    void shouldNotOverwriteExistingAccount() throws Exception {
        UserMapper userMapper = mock(UserMapper.class);
        AuthModeProperties properties = localAdminProperties();
        User existing = new User();
        existing.setUsername("admin");
        existing.setPassword("existing-hash");
        when(userMapper.selectOne(any(Wrapper.class))).thenReturn(existing);
        LocalAdminInitializer initializer = new LocalAdminInitializer(userMapper, properties);

        initializer.run(null);

        verify(userMapper, never()).insert(any(User.class));
        verify(userMapper, never()).updateById(any(User.class));
        assertThat(existing.getPassword()).isEqualTo("existing-hash");
    }

    @Test
    @DisplayName("邮箱验证模式不创建默认管理员")
    void shouldSkipBootstrapOutsideLocalMode() throws Exception {
        UserMapper userMapper = mock(UserMapper.class);
        AuthModeProperties properties = localAdminProperties();
        properties.setMode("email-verified");
        LocalAdminInitializer initializer = new LocalAdminInitializer(userMapper, properties);

        initializer.run(null);

        verify(userMapper, never()).selectOne(any(Wrapper.class));
        verify(userMapper, never()).insert(any(User.class));
    }

    private AuthModeProperties localAdminProperties() {
        AuthModeProperties properties = new AuthModeProperties();
        properties.setMode("local-admin");
        properties.getLocalAdmin().setUsername("admin");
        properties.getLocalAdmin().setPassword("admin123");
        return properties;
    }
}
