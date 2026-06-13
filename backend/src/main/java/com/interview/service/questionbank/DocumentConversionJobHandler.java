package com.interview.service.questionbank;

import com.interview.entity.AppJob;
import com.interview.entity.KnowledgeSourceFile;
import com.interview.mapper.KnowledgeSourceFileMapper;
import com.interview.service.AppJobHandler;
import com.interview.service.AppJobService;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.Locale;
import java.util.regex.Pattern;

@Service
public class DocumentConversionJobHandler implements AppJobHandler {

    public static final String JOB_TYPE = "IMPORT_FILE";
    private static final Pattern SENSITIVE_PATTERN = Pattern.compile(
            "(?i)(Authorization\\s*[:=]\\s*\\S+|Bearer\\s+\\S+|api_key\\s*[:=]\\s*\\S+|sk-[A-Za-z0-9_-]+)"
    );
    private static final String CONVERTER_UNAVAILABLE_MESSAGE = "文档转换服务暂不可用，请确认 document-converter 已启动";

    private final KnowledgeSourceFileMapper sourceFileMapper;
    private final FileStorageService fileStorageService;
    private final DocumentConverterClient converterClient;
    private final AppJobService appJobService;

    public DocumentConversionJobHandler(KnowledgeSourceFileMapper sourceFileMapper,
                                        FileStorageService fileStorageService,
                                        DocumentConverterClient converterClient,
                                        AppJobService appJobService) {
        this.sourceFileMapper = sourceFileMapper;
        this.fileStorageService = fileStorageService;
        this.converterClient = converterClient;
        this.appJobService = appJobService;
    }

    @Override
    public String jobType() {
        return JOB_TYPE;
    }

    @Override
    public void handle(AppJob job) {
        if (job.getSourceFileId() == null) {
            throw new RuntimeException("转换作业缺少源文件");
        }
        KnowledgeSourceFile sourceFile = sourceFileMapper.selectById(job.getSourceFileId());
        if (sourceFile == null) {
            throw new RuntimeException("源文件不存在");
        }
        try {
            sourceFile.setStatus("CONVERTING");
            sourceFile.setErrorMessage(null);
            sourceFileMapper.updateById(sourceFile);
            appJobService.updateRunningJob(job.getId(), job.getClaimedBy(), "CONVERTING", 40);

            String markdown = converterClient.convertToMarkdown(sourceFile);
            FileStorageService.StoredFile storedMarkdown = fileStorageService.storeText(
                    markdown,
                    "knowledge/markdown",
                    markdownFilename(sourceFile.getOriginalFilename())
            );
            sourceFile.setMarkdownStorageKey(storedMarkdown.storageKey());
            sourceFile.setStatus("CONVERTED");
            sourceFile.setErrorMessage(null);
            sourceFileMapper.updateById(sourceFile);
            appJobService.updateRunningJob(job.getId(), job.getClaimedBy(), "CONVERTED", 90);
        } catch (IOException | RuntimeException e) {
            sourceFile.setStatus("FAILED");
            sourceFile.setErrorMessage(sanitize(e.getMessage()));
            sourceFileMapper.updateById(sourceFile);
            throw new RuntimeException("文档转换失败：" + sanitize(e.getMessage()), e);
        }
    }

    private String markdownFilename(String originalFilename) {
        String filename = originalFilename == null || originalFilename.isBlank() ? "converted" : originalFilename;
        int dot = filename.lastIndexOf('.');
        if (dot > 0) {
            filename = filename.substring(0, dot);
        }
        return filename.toLowerCase(Locale.ROOT).endsWith(".md") ? filename : filename + ".md";
    }

    private String sanitize(String message) {
        if (message == null) {
            return "文档转换失败";
        }
        if (isConverterUnavailable(message)) {
            return CONVERTER_UNAVAILABLE_MESSAGE;
        }
        return SENSITIVE_PATTERN.matcher(message).replaceAll("[REDACTED]");
    }

    private boolean isConverterUnavailable(String message) {
        String normalized = message.toLowerCase(Locale.ROOT);
        return normalized.contains("connection refused")
                || normalized.contains("connect timed out")
                || normalized.contains("i/o error on post request");
    }
}
