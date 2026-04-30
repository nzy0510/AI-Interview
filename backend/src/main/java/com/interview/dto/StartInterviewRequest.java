package com.interview.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.List;

@Data
public class StartInterviewRequest {
    @NotBlank(message = "岗位不能为空")
    private String position;

    private String mode = "text";

    private List<String> resumeQuestions;

    private String difficultyLevel = "mid";

    private List<String> focusAreas;
}
