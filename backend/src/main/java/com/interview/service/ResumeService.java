package com.interview.service;

import com.interview.dto.ResumeProfileResponse;
import org.springframework.web.multipart.MultipartFile;
import java.util.Map;

public interface ResumeService {
    /**
     * 解析上传的简历 PDF，并提取结构化大模型画像与定制化面试题。
     * Phase 2 起分析本身不需要 positionId，但调用方需提前校验岗位可见性。
     */
    Map<String, Object> parseAndAnalyze(Long userId, MultipartFile file) throws Exception;

    /**
     * 按岗位 UPSERT 简历画像：同一用户+岗位有则更新，无则插入。
     * Phase 2 隔离键为 userId + positionId。
     */
    void saveOrUpdateProfile(Long userId, Long positionId, String positionName, String analysisJson);

    /**
     * 查询用户指定岗位的简历画像。
     * Phase 2 起返回包装响应，包含画像数据和岗位元信息；无画像返回 null。
     */
    ResumeProfileResponse getProfileByUserIdAndPosition(Long userId, Long positionId);

    /**
     * 删除用户指定岗位的简历画像。
     */
    void deleteProfileByUserIdAndPosition(Long userId, Long positionId);

    // ─── 兼容旧接口：Phase 2 不应再使用，仅保留供过渡期或测试 ───

    /** @deprecated 使用 getProfileByUserIdAndPosition(userId, positionId) */
    @Deprecated
    Object getProfileByUserId(Long userId);

    /** @deprecated 使用 deleteProfileByUserIdAndPosition(userId, positionId) */
    @Deprecated
    void deleteProfileByUserId(Long userId);
}
