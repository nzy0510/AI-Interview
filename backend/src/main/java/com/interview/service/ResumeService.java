package com.interview.service;

import org.springframework.web.multipart.MultipartFile;
import java.util.Map;

public interface ResumeService {
    /**
     * 解析上传的简历 PDF，并提取结构化大模型画像与定制化面试题
     */
    Map<String, Object> parseAndAnalyze(MultipartFile file) throws Exception;

    /** UPSERT 简历画像：用户有则更新，无则插入 */
    void saveOrUpdateProfile(Long userId, String position, String analysisJson);

    /** 查询用户简历画像，返回解析后的 JSON，无画像返回 null */
    Object getProfileByUserId(Long userId);

    /** 删除用户简历画像 */
    void deleteProfileByUserId(Long userId);
}
