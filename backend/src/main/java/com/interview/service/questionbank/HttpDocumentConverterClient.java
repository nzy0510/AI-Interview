package com.interview.service.questionbank;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.interview.config.ExternalHttpClientFactory;
import com.interview.entity.KnowledgeSourceFile;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

@Service
public class HttpDocumentConverterClient implements DocumentConverterClient {

    private final FileStorageService fileStorageService;
    private final RestTemplate restTemplate;
    private final String endpoint;

    public HttpDocumentConverterClient(FileStorageService fileStorageService,
                                       @Value("${app.document-converter.url:http://localhost:8010}") String baseUrl,
                                       @Value("${app.document-converter.connect-timeout-ms:3000}") int connectTimeoutMs,
                                       @Value("${app.document-converter.read-timeout-ms:30000}") int readTimeoutMs) {
        this.fileStorageService = fileStorageService;
        this.restTemplate = ExternalHttpClientFactory.create(connectTimeoutMs, readTimeoutMs);
        this.endpoint = baseUrl.replaceAll("/+$", "") + "/convert";
    }

    @Override
    public String convertToMarkdown(KnowledgeSourceFile sourceFile) {
        Resource resource = fileStorageService.loadAsResource(sourceFile.getStorageKey());
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", resource);
        body.add("filename", sourceFile.getOriginalFilename());

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        String response = restTemplate.postForObject(endpoint, new HttpEntity<>(body, headers), String.class);
        return parseMarkdown(response);
    }

    private String parseMarkdown(String response) {
        if (response == null || response.isBlank()) {
            throw new RuntimeException("文档转换服务返回空内容");
        }
        String trimmed = response.trim();
        if (!trimmed.startsWith("{")) {
            return response;
        }
        JSONObject json = JSON.parseObject(trimmed);
        String markdown = json.getString("markdown");
        if (markdown == null) {
            markdown = json.getString("content");
        }
        if (markdown == null) {
            throw new RuntimeException("文档转换服务响应缺少 markdown 字段");
        }
        return markdown;
    }
}
