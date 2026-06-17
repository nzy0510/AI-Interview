package com.interview.controller;

import com.interview.common.Result;
import com.interview.dto.ResumeProfileResponse;
import com.interview.entity.InterviewPosition;
import com.interview.exception.LlmProviderRequiredException;
import com.interview.mapper.InterviewPositionMapper;
import com.interview.service.ResumeService;
import com.interview.service.UserLlmConfigService;
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
 * 简历控制器：按岗位上传解析 + 持久化画像 + 查询 + 删除。
 * Phase 2 起所有简历 API 强制要求 positionId，不支持全局单例模式。
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
    private UserLlmConfigService userLlmConfigService;

    @Autowired
    private InterviewPositionMapper interviewPositionMapper;

    /**
     * 上传并解析简历（首次上传或覆盖更新）。
     * Phase 2 起强制要求 positionId；解析成功后按 userId+positionId 持久化。
     */
    @PostMapping("/parse")
    public Result<?> parseResume(@RequestParam("file") MultipartFile file,
                                 @RequestParam("positionId") Long positionId,
                                 @RequestParam(value = "position", defaultValue = "软件开发") String positionName,
                                 HttpServletRequest request) {
        Result<?> validationError = validateResumeFile(file);
        if (validationError != null) {
            return validationError;
        }
        if (positionId == null || positionId <= 0) {
            return Result.error(400, "请选择岗位后再上传简历");
        }
        try {
            Long userId = getUserIdFromRequest(request);
            userLlmConfigService.ensureActiveProvider(userId);

            // 校验岗位可见性
            validatePositionVisible(userId, positionId);

            Map<String, Object> analysisResult = resumeService.parseAndAnalyze(userId, file);

            // 持久化到数据库（UPSERT by userId + positionId）
            try {
                String analysisJson = com.alibaba.fastjson2.JSON.toJSONString(analysisResult);
                resumeService.saveOrUpdateProfile(userId, positionId, positionName, analysisJson);
            } catch (Exception dbErr) {
                log.warn("简历画像持久化失败（不影响前端展示）: {}", dbErr.getMessage());
            }

            return Result.success(analysisResult);
        } catch (LlmProviderRequiredException e) {
            throw e;
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
     * 获取当前用户指定岗位的简历画像。
     * Phase 2 起强制要求 positionId，返回包装响应含岗位元信息。
     */
    @GetMapping("/profile")
    public Result<?> getProfile(@RequestParam("positionId") Long positionId,
                                HttpServletRequest request) {
        if (positionId == null || positionId <= 0) {
            return Result.error(400, "请选择岗位后查看简历画像");
        }
        try {
            Long userId = getUserIdFromRequest(request);
            validatePositionVisible(userId, positionId);
            ResumeProfileResponse resp = resumeService.getProfileByUserIdAndPosition(userId, positionId);
            return Result.success(resp);
        } catch (IllegalArgumentException e) {
            return Result.error(400, e.getMessage());
        } catch (Exception e) {
            log.warn("获取简历画像失败: {}", e.getMessage());
            return Result.success(null);
        }
    }

    /**
     * 删除当前用户指定岗位的简历画像。
     */
    @DeleteMapping("/profile")
    public Result<?> deleteProfile(@RequestParam("positionId") Long positionId,
                                   HttpServletRequest request) {
        if (positionId == null || positionId <= 0) {
            return Result.error(400, "请选择岗位后删除简历画像");
        }
        Long userId = getUserIdFromRequest(request);
        validatePositionVisible(userId, positionId);
        resumeService.deleteProfileByUserIdAndPosition(userId, positionId);
        return Result.success("简历画像已清除");
    }

    /**
     * 校验岗位对当前用户可见（公共岗位或自有私有岗位）。
     */
    private void validatePositionVisible(Long userId, Long positionId) {
        if (positionId == null) {
            throw new IllegalArgumentException("岗位 ID 不能为空");
        }
        InterviewPosition position = interviewPositionMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<InterviewPosition>()
                        .eq(InterviewPosition::getId, positionId)
                        .eq(InterviewPosition::getStatus, "ACTIVE")
                        .and(wrapper -> wrapper
                                .eq(InterviewPosition::getScope, "PUBLIC")
                                .or(nested -> nested
                                        .eq(InterviewPosition::getScope, "PRIVATE")
                                        .eq(InterviewPosition::getOwnerUserId, userId))));
        if (position == null) {
            throw new IllegalArgumentException("岗位不存在或无权访问");
        }
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
