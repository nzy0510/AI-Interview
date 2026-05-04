package com.interview.controller;

import com.interview.common.Result;
import com.interview.service.ResumeService;
import com.interview.utils.JwtUtils;
import io.jsonwebtoken.Claims;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import jakarta.servlet.http.HttpServletRequest;

import java.util.Map;

/**
 * 简历控制器：上传解析 + 持久化画像 + 查询 + 更新覆盖
 */
@Slf4j
@RestController
@RequestMapping("/api/resume")
public class ResumeController {

    @Autowired
    private ResumeService resumeService;

    @Autowired
    private JwtUtils jwtUtils;

    /**
     * 上传并解析简历（首次上传或覆盖更新）
     * 解析成功后自动存入 resume_profile 表
     */
    @PostMapping("/parse")
    public Result<?> parseResume(@RequestParam("file") MultipartFile file,
                                 @RequestParam(value = "position", defaultValue = "软件开发") String position,
                                 HttpServletRequest request) {
        if (file.isEmpty()) {
            return Result.error("文件不能为空");
        }
        try {
            Long userId = getUserIdFromRequest(request);
            Map<String, Object> analysisResult = resumeService.parseAndAnalyze(file);

            // 持久化到数据库（UPSERT）
            try {
                String analysisJson = com.alibaba.fastjson2.JSON.toJSONString(analysisResult);
                resumeService.saveOrUpdateProfile(userId, position, analysisJson);
            } catch (Exception dbErr) {
                log.warn("简历画像持久化失败（不影响前端展示）: {}", dbErr.getMessage());
            }

            return Result.success(analysisResult);
        } catch (Exception e) {
            e.printStackTrace();
            return Result.error(e.getMessage());
        }
    }

    /**
     * 获取当前用户最新的简历画像（用于页面加载时替代 localStorage）
     */
    @GetMapping("/profile")
    public Result<?> getProfile(HttpServletRequest request) {
        try {
            Long userId = getUserIdFromRequest(request);
            Object parsed = resumeService.getProfileByUserId(userId);
            return Result.success(parsed);
        } catch (Exception e) {
            log.warn("获取简历画像失败: {}", e.getMessage());
            return Result.success(null);
        }
    }

    /**
     * 删除当前用户的简历画像
     */
    @DeleteMapping("/profile")
    public Result<?> deleteProfile(HttpServletRequest request) {
        Long userId = getUserIdFromRequest(request);
        resumeService.deleteProfileByUserId(userId);
        return Result.success("简历画像已清除");
    }

    private Long getUserIdFromRequest(HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("currentUserId");
        if (userId != null) return userId;
        // 兼容拦截器未覆盖的场景
        String token = request.getHeader("Authorization");
        if (token != null && token.startsWith("Bearer ")) {
            token = token.substring(7);
        } else {
            token = request.getParameter("token");
        }
        if (token == null || token.isEmpty()) {
            throw new RuntimeException("未登录：缺少 Token");
        }
        Claims claims = jwtUtils.parseJwt(token);
        return ((Number) claims.get("id")).longValue();
    }
}
