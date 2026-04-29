package com.interview.service.impl;

import com.alibaba.fastjson2.JSON;
import com.interview.service.ResumeService;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.output.Response;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.util.List;
import java.util.Map;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.interview.entity.ResumeProfile;
import com.interview.mapper.ResumeProfileMapper;
import java.time.LocalDateTime;

@Slf4j
@Service
public class ResumeServiceImpl implements ResumeService {

    @Autowired
    private ChatLanguageModel chatModel;

    @Autowired
    private ResumeProfileMapper resumeProfileMapper;

    @Autowired
    private com.interview.config.InterviewPrompts interviewPrompts;

    /** 仅用于测试注入 Mock Mapper */
    public void setResumeProfileMapper(ResumeProfileMapper mapper) {
        this.resumeProfileMapper = mapper;
    }

    @Override
    public Map<String, Object> parseAndAnalyze(MultipartFile file) throws Exception {
        // 1. 读取 PDF 纯文本
        String rawText = "";
        try (InputStream is = file.getInputStream(); PDDocument document = PDDocument.load(is)) {
            PDFTextStripper stripper = new PDFTextStripper();
            rawText = stripper.getText(document);
        } catch (Exception e) {
            log.error("PDF 读取与解析失败", e);
            throw new Exception("简历读取失败，请确保上传了合法的 PDF 格式文件: " + e.getMessage());
        }

        // 为了防止大模型溢出，截断超长部分 (通常一两页的简历长度不会超过6000字)
        if (rawText.length() > 6000) {
            rawText = rawText.substring(0, 6000);
        }

        log.info("PDF 文件解析完成，提取字符数: {}", rawText.length());

        // 2. 构造大模型分析画像的 Prompt
        String prompt = interviewPrompts.getResumeAnalysis();

        // 3. 呼叫大模型执行生成
        try {
            Response<dev.langchain4j.data.message.AiMessage> aiResponse = chatModel.generate(
                    List.of(
                            new SystemMessage(prompt),
                            new UserMessage("【原始简历内容】\\n" + rawText)
                    )
            );
            
            String jsonStr = aiResponse.content().text().trim();
            // 鲁棒性处理：剔除可能包含的 markdown 语法块
            if (jsonStr.startsWith("```json")) {
                jsonStr = jsonStr.substring(7);
            }
            if (jsonStr.startsWith("```")) {
                jsonStr = jsonStr.substring(3);
            }
            if (jsonStr.endsWith("```")) {
                jsonStr = jsonStr.substring(0, jsonStr.length() - 3);
            }
            
            return JSON.parseObject(jsonStr.trim(), Map.class);
        } catch (Exception e) {
            log.error("大模型生成简历画像失败", e);
            throw new Exception("AI生成简历画像异常，请重试。" + e.getMessage());
        }
    }

    @Override
    public void saveOrUpdateProfile(Long userId, String position, String analysisJson) {
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
    }

    @Override
    public Object getProfileByUserId(Long userId) {
        LambdaQueryWrapper<ResumeProfile> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ResumeProfile::getUserId, userId);
        ResumeProfile profile = resumeProfileMapper.selectOne(wrapper);
        if (profile == null) return null;
        return JSON.parse(profile.getAnalysisJson());
    }

    @Override
    public void deleteProfileByUserId(Long userId) {
        LambdaQueryWrapper<ResumeProfile> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ResumeProfile::getUserId, userId);
        resumeProfileMapper.delete(wrapper);
    }
}
