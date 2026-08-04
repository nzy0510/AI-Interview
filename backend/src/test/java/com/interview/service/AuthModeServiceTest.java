package com.interview.service;

import com.interview.config.AuthModeProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("AuthModeService — 认证模式")
class AuthModeServiceTest {

    @Test
    @DisplayName("本地管理员模式关闭邮箱注册与密码找回")
    void shouldDisableEmailFlowsInLocalAdminMode() {
        AuthModeProperties properties = new AuthModeProperties();
        properties.setMode("local-admin");
        AuthModeService service = new AuthModeService(properties);

        AuthModeService.PublicAuthConfig config = service.getPublicConfig();

        assertThat(config.mode()).isEqualTo("local-admin");
        assertThat(config.registrationEnabled()).isFalse();
        assertThat(config.passwordResetEnabled()).isFalse();
        assertThatThrownBy(service::requireEmailAuthentication)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("本地管理员模式");
    }

    @Test
    @DisplayName("邮箱验证模式保留注册与密码找回")
    void shouldKeepEmailFlowsInEmailVerifiedMode() {
        AuthModeProperties properties = new AuthModeProperties();
        properties.setMode("email-verified");
        AuthModeService service = new AuthModeService(properties);

        AuthModeService.PublicAuthConfig config = service.getPublicConfig();

        assertThat(config.mode()).isEqualTo("email-verified");
        assertThat(config.registrationEnabled()).isTrue();
        assertThat(config.passwordResetEnabled()).isTrue();
        service.requireEmailAuthentication();
    }

    @Test
    @DisplayName("未知认证模式应明确失败")
    void shouldRejectUnknownMode() {
        AuthModeProperties properties = new AuthModeProperties();
        properties.setMode("unexpected");

        assertThatThrownBy(properties::isLocalAdminMode)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("APP_AUTH_MODE");
    }
}
