package com.interview.service;

import org.springframework.web.multipart.MultipartFile;
import java.util.Map;

public interface ResumeService {
    /**
     * 解析上传的简历 PDF，并提取结构化大模型画像与定制化面试题
     */
    Map<String, Object> parseAndAnalyze(MultipartFile file) throws Exception;
}
