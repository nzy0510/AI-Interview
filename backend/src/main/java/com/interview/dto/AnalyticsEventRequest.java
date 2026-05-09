package com.interview.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.Map;

@Data
public class AnalyticsEventRequest {
    @NotBlank(message = "事件类型不能为空")
    @Size(max = 64, message = "事件类型过长")
    private String eventType;

    @Size(max = 64, message = "事件分类过长")
    private String category;

    @Size(max = 500, message = "页面地址过长")
    private String pageUrl;

    private Map<String, Object> metadata;
}
