package com.interview.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class FinishInterviewRequest {
    @NotNull(message = "面试记录 ID 不能为空")
    private Long recordId;

    private Integer wpm;
    private String emotionJson;
}
