package com.interview.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class FeedbackRequest {
    @Size(max = 64, message = "反馈类型过长")
    private String category;

    @NotBlank(message = "反馈内容不能为空")
    @Size(max = 2000, message = "反馈内容不能超过2000字")
    private String content;

    @Size(max = 255, message = "联系方式过长")
    private String contact;

    @Size(max = 500, message = "页面地址过长")
    private String pageUrl;
}
