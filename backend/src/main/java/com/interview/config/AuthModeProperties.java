package com.interview.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.auth")
public class AuthModeProperties {

    public static final String LOCAL_ADMIN_MODE = "local-admin";
    public static final String EMAIL_VERIFIED_MODE = "email-verified";
    public static final String LOCAL_ADMIN_USERNAME = "admin";
    public static final String LOCAL_ADMIN_PASSWORD = "admin123";

    private String mode = EMAIL_VERIFIED_MODE;

    public String getMode() {
        return mode;
    }

    public void setMode(String mode) {
        this.mode = mode;
    }

    public boolean isLocalAdminMode() {
        if (LOCAL_ADMIN_MODE.equalsIgnoreCase(mode)) {
            return true;
        }
        if (EMAIL_VERIFIED_MODE.equalsIgnoreCase(mode)) {
            return false;
        }
        throw new IllegalStateException(
                "APP_AUTH_MODE 仅支持 local-admin 或 email-verified，当前值: " + mode);
    }
}
