package com.interview.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.interview.common.Result;
import com.interview.entity.ResumeProfile;
import com.interview.mapper.ResumeProfileMapper;
import com.interview.service.ResumeService;
import com.interview.utils.JwtUtils;
import io.jsonwebtoken.Claims;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import jakarta.servlet.http.HttpServletRequest;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 简历控制器：上传解析 + 持久化画像 + 查询 + 更新覆盖
 */
@RestController
@RequestMapping("/api/resume")
@CrossOrigin(origins = "*")
public class ResumeController {

    @Autowired
    private ResumeService resumeService;

    @Autowired
    private ResumeProfileMapper resumeProfileMapper;

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

            // 持久化到数据库（UPSERT：有则更新，无则新建）
            // 即使 DB 保存失败，也不影响返回解析结果给前端
            try {
                String analysisJson = com.alibaba.fastjson2.JSON.toJSONString(analysisResult);
                LambdaQueryWrapper<ResumeProfile> wrapper = new LambdaQueryWrapper<>();
                wrapper.eq(ResumeProfile::getUserId, userId);
                ResumeProfile existing = resumeProfileMapper.selectOne(wrapper);

                if (existing != null) {
                    existing.setPosition(position);
                    existing.setAnalysisJson(analysisJson);
                    existing.setUpdateTime(LocalDateTime.now());
                    resumeProfileMapper.updateById(existing);
                } else {
                    ResumeProfile profile = new ResumeProfile();
                    profile.setUserId(userId);
                    profile.setPosition(position);
                    profile.setAnalysisJson(analysisJson);
                    profile.setCreateTime(LocalDateTime.now());
                    profile.setUpdateTime(LocalDateTime.now());
                    resumeProfileMapper.insert(profile);
                }
            } catch (Exception dbErr) {
                // DB 保存失败仅记录日志，不阻塞用户操作
                System.err.println("⚠️ 简历画像持久化失败（不影响前端展示）: " + dbErr.getMessage());
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
            LambdaQueryWrapper<ResumeProfile> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(ResumeProfile::getUserId, userId);
            ResumeProfile profile = resumeProfileMapper.selectOne(wrapper);
            if (profile == null) {
                return Result.success(null); // 该用户尚未上传过简历
            }
            // 返回解析后的 JSON 对象（而非字符串）
            Object parsed = com.alibaba.fastjson2.JSON.parse(profile.getAnalysisJson());
            return Result.success(parsed);
        } catch (Exception e) {
            System.err.println("⚠️ 获取简历画像失败: " + e.getMessage());
            return Result.success(null);
        }
    }

    /**
     * 删除当前用户的简历画像
     */
    @DeleteMapping("/profile")
    public Result<?> deleteProfile(HttpServletRequest request) {
        Long userId = getUserIdFromRequest(request);
        LambdaQueryWrapper<ResumeProfile> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ResumeProfile::getUserId, userId);
        resumeProfileMapper.delete(wrapper);
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
        Claims claims = JwtUtils.parseJwt(token);
        return ((Number) claims.get("id")).longValue();
    }
}
