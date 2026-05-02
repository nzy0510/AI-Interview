package com.interview.controller;

import com.interview.common.Result;
import com.interview.dto.LoginDTO;
import com.interview.dto.MentorInsightResponse;
import com.interview.dto.RegisterDTO;
import com.interview.dto.ResetPasswordDTO;
import com.interview.entity.UserPreference;
import com.interview.service.MentorService;
import com.interview.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

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

    /**
     * 强制刷新 AI Mentor 洞察报告，绕过 24 小时缓存。
     */
    @PostMapping("/mentor-insight/refresh")
    public Result<MentorInsightResponse> refreshMentorInsight(HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("currentUserId");
        MentorInsightResponse insight = mentorService.getInsight(userId, true);
        return Result.success(insight);
    }

    /** 更新用户资料（昵称/邮箱） */
    @PutMapping("/profile")
    public Result<String> updateProfile(@RequestBody Map<String, String> body, HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("currentUserId");
        userService.updateProfile(userId, body.get("nickname"), body.get("email"));
        return Result.success("资料已更新");
    }

    /** 修改密码 */
    @PutMapping("/password")
    public Result<String> changePassword(@RequestBody Map<String, String> body, HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("currentUserId");
        userService.changePassword(userId, body.get("oldPassword"), body.get("newPassword"));
        return Result.success("密码已修改");
    }

    /** 获取当前用户信息 */
    @GetMapping("/me")
    public Result<Map<String, Object>> getCurrentUser(HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("currentUserId");
        var user = userService.getById(userId);
        return Result.success(Map.of(
            "id", user.getId(),
            "username", user.getUsername(),
            "nickname", user.getNickname() != null ? user.getNickname() : user.getUsername(),
            "email", user.getEmail() != null ? user.getEmail() : "",
            "avatar", user.getAvatar() != null ? user.getAvatar() : ""
        ));
    }

    /** 获取面试偏好 */
    @GetMapping("/preference")
    public Result<UserPreference> getPreference(HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("currentUserId");
        return Result.success(userService.getPreference(userId));
    }

    /** 保存面试偏好 */
    @PutMapping("/preference")
    public Result<String> updatePreference(@RequestBody UserPreference pref, HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("currentUserId");
        userService.updatePreference(userId, pref);
        return Result.success("偏好已保存");
    }

    /** 上传用户头像 */
    @PostMapping("/avatar")
    public Result<Map<String, String>> uploadAvatar(
            @RequestParam("file") MultipartFile file,
            HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("currentUserId");
        String avatarUrl = userService.uploadAvatar(userId, file);
        return Result.success(Map.of("avatarUrl", avatarUrl));
    }
}
