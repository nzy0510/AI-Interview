package com.interview.controller;

import com.interview.common.Result;
import com.interview.service.ResumeService;
import com.interview.service.UsageQuotaService;
import com.interview.utils.JwtUtils;
import io.jsonwebtoken.Claims;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import jakarta.servlet.http.HttpServletRequest;

import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;
import java.util.Map;

/**
 * 简历控制器：上传解析 + 持久化画像 + 查询 + 更新覆盖
 */
@Slf4j
@RestController
@RequestMapping("/api/resume")
public class ResumeController {

    private static final long MAX_RESUME_FILE_SIZE_BYTES = 5 * 1024 * 1024;

    @Autowired
    private ResumeService resumeService;

    @Autowired
    private JwtUtils jwtUtils;

    @Autowired
    private UsageQuotaService usageQuotaService;

    /**
     * 上传并解析简历（首次上传或覆盖更新）
     * 解析成功后自动存入 resume_profile 表
     */
    @PostMapping("/parse")
    public Result<?> parseResume(@RequestParam("file") MultipartFile file,
                                 @RequestParam(value = "position", defaultValue = "软件开发") String position,
                                 HttpServletRequest request) {
        Result<?> validationError = validateResumeFile(file);
        if (validationError != null) {
            return validationError;
        }
        try {
            Long userId = getUserIdFromRequest(request);
            usageQuotaService.consume(userId, UsageQuotaService.RESUME_PARSE);
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
            log.warn("简历解析失败: {}", e.getMessage());
            return Result.error(500, "简历解析失败，请稍后重试");
        }
    }

    private Result<?> validateResumeFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return Result.error(400, "文件不能为空");
        }
        if (file.getSize() > MAX_RESUME_FILE_SIZE_BYTES) {
            return Result.error(400, "PDF 简历文件不能超过 5MB");
        }

        String filename = file.getOriginalFilename() != null ? file.getOriginalFilename().toLowerCase(Locale.ROOT) : "";
        String contentType = file.getContentType() != null ? file.getContentType().toLowerCase(Locale.ROOT) : "";
        if (!filename.endsWith(".pdf") || !isPdfContentType(contentType) || !hasPdfHeader(file)) {
            return Result.error(400, "仅支持 PDF 格式简历");
        }
        return null;
    }

    private boolean isPdfContentType(String contentType) {
        return "application/pdf".equals(contentType) || "application/x-pdf".equals(contentType);
    }

    private boolean hasPdfHeader(MultipartFile file) {
        try (InputStream inputStream = file.getInputStream()) {
            byte[] header = inputStream.readNBytes(4);
            return header.length == 4
                    && header[0] == '%'
                    && header[1] == 'P'
                    && header[2] == 'D'
                    && header[3] == 'F';
        } catch (IOException e) {
            log.warn("读取简历文件头失败: {}", e.getMessage());
            return false;
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
