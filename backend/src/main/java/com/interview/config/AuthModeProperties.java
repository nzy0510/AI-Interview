package com.interview.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.auth")
public class AuthModeProperties {

    public static final String LOCAL_ADMIN_MODE = "local-admin";
    public static final String EMAIL_VERIFIED_MODE = "email-verified";

    private String mode = EMAIL_VERIFIED_MODE;
    private LocalAdmin localAdmin = new LocalAdmin();

    public String getMode() {
        return mode;
    }

    public void setMode(String mode) {
        this.mode = mode;
    }

    public LocalAdmin getLocalAdmin() {
        return localAdmin;
    }

    public void setLocalAdmin(LocalAdmin localAdmin) {
        this.localAdmin = localAdmin;
    }

    public boolean isLocalAdminMode() {
        return LOCAL_ADMIN_MODE.equalsIgnoreCase(mode);
    }

    public static class LocalAdmin {
        private String username = "admin";
        private String password = "admin123";

        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = username;
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }
    }
}
