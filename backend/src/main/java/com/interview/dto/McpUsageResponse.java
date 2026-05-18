package com.interview.dto;

import lombok.Data;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Data
public class McpUsageResponse {
    private LocalDate date;
    private List<McpTokenResponse.QuotaItem> items = new ArrayList<>();
}
