package com.interview.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class McpTokenResponse {
    private boolean hasActiveToken;
    private String token;
    private String tokenPrefix;
    private String role;
    private String endpointUrl;
    private String claudeCommand;
    private String jsonConfig;
    private LocalDateTime createTime;
    private LocalDateTime lastUsedAt;
    private List<QuotaItem> quotas;

    @Data
    public static class QuotaItem {
        private String type;
        private String label;
        private int used;
        private int limit;
        private int remaining;
    }
}
