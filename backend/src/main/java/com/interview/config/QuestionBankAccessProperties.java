package com.interview.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.question-bank")
public class QuestionBankAccessProperties {

    private boolean userMaintenanceEnabled = false;

    public boolean isUserMaintenanceEnabled() {
        return userMaintenanceEnabled;
    }

    public void setUserMaintenanceEnabled(boolean userMaintenanceEnabled) {
        this.userMaintenanceEnabled = userMaintenanceEnabled;
    }
}
