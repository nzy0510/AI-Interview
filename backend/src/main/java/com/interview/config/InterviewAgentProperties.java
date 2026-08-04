package com.interview.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app.interview-agent")
public class InterviewAgentProperties {

    private boolean enabled = true;
    private int planningTimeoutSeconds = 35;
    private int maxToolCalls = 3;
    private boolean fallbackEnabled = true;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public int getPlanningTimeoutSeconds() {
        return planningTimeoutSeconds;
    }

    public void setPlanningTimeoutSeconds(int planningTimeoutSeconds) {
        this.planningTimeoutSeconds = planningTimeoutSeconds;
    }

    public int getMaxToolCalls() {
        return maxToolCalls;
    }

    public void setMaxToolCalls(int maxToolCalls) {
        this.maxToolCalls = maxToolCalls;
    }

    public boolean isFallbackEnabled() {
        return fallbackEnabled;
    }

    public void setFallbackEnabled(boolean fallbackEnabled) {
        this.fallbackEnabled = fallbackEnabled;
    }
}
