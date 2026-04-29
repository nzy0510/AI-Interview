package com.interview.controller;

import com.interview.common.Result;
import com.interview.dto.LoginDTO;
import com.interview.dto.MentorInsightResponse;
import com.interview.dto.RegisterDTO;
import com.interview.dto.ResetPasswordDTO;
import com.interview.service.MentorService;
import com.interview.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/user")
@CrossOrigin
public class UserController {

    @Autowired
    private UserService userService;

    @Autowired
    private MentorService mentorService;

    @PostMapping("/login")
    public Result<String> login(@RequestBody @Validated LoginDTO loginDTO) {
        String token = userService.login(loginDTO);
        return Result.success(token);
    }

    @PostMapping("/register")
    public Result<String> register(@RequestBody @Validated RegisterDTO registerDTO) {
        userService.register(registerDTO);
        return Result.success("注册成功");
    }

    /**
     * 发送邮箱验证码（注册时使用）
     */
    @PostMapping("/send-code")
    public Result<String> sendCode(@RequestBody Map<String, String> body) {
        String email = body.get("email");
        if (email == null || email.isBlank()) {
            throw new RuntimeException("邮箱不能为空");
        }
        String purpose = body.getOrDefault("purpose", "注册");
        userService.sendVerificationCode(email, purpose);
        return Result.success("验证码已发送");
    }

    /**
     * 忘记密码 - 发送重置验证码
     */
    @PostMapping("/forgot-password")
    public Result<String> forgotPassword(@RequestBody Map<String, String> body) {
        String email = body.get("email");
        if (email == null || email.isBlank()) {
            throw new RuntimeException("邮箱不能为空");
        }
        userService.forgotPassword(email);
        return Result.success("重置验证码已发送至您的邮箱");
    }

    /**
     * 重置密码
     */
    @PostMapping("/reset-password")
    public Result<String> resetPassword(@RequestBody @Validated ResetPasswordDTO resetDTO) {
        userService.resetPassword(resetDTO);
        return Result.success("密码重置成功，请使用新密码登录");
    }

    /**
     * 知识覆盖统计（快速，仅查数据库，不调 LLM）
     */
    @GetMapping("/knowledge-coverage")
    public Result<MentorInsightResponse> knowledgeCoverage(HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("currentUserId");
        MentorInsightResponse insight = mentorService.getKnowledgeCoverageOnly(userId);
        return Result.success(insight);
    }

    /**
     * AI Mentor 洞察报告（含 LLM 分析，24小时缓存）
     */
    @GetMapping("/mentor-insight")
    public Result<MentorInsightResponse> mentorInsight(HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("currentUserId");
        MentorInsightResponse insight = mentorService.getInsight(userId);
        return Result.success(insight);
    }
}
