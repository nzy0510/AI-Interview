package com.interview.service;

import com.interview.config.AuthModeProperties;
import org.springframework.stereotype.Service;

@Service
public class AuthModeService {

    private final AuthModeProperties properties;

    public AuthModeService(AuthModeProperties properties) {
        this.properties = properties;
    }

    public PublicAuthConfig getPublicConfig() {
        boolean emailAuthentication = !properties.isLocalAdminMode();
        String mode = properties.isLocalAdminMode()
                ? AuthModeProperties.LOCAL_ADMIN_MODE
                : AuthModeProperties.EMAIL_VERIFIED_MODE;
        return new PublicAuthConfig(mode, emailAuthentication, emailAuthentication);
    }

    public void requireEmailAuthentication() {
        if (properties.isLocalAdminMode()) {
            throw new IllegalStateException("本地管理员模式已关闭邮箱注册与密码找回");
        }
    }

    public record PublicAuthConfig(
            String mode,
            boolean registrationEnabled,
            boolean passwordResetEnabled) {
    }
}
