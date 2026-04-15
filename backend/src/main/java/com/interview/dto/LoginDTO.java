package com.interview.dto;

import lombok.Data;
import jakarta.validation.constraints.NotBlank;

@Data
public class LoginDTO {
    @NotBlank(message = "登录账号不能为空")
    private String username;
    
    @NotBlank(message = "密码不能为空")
    private String password;
}
