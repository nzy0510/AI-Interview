package com.interview.service;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.EmbeddingStoreIngestor;
import dev.langchain4j.data.document.splitter.DocumentSplitters;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * RAG 知识库加载服务：Spring Boot 启动时自动执行
 * 职责：读取 resources/knowledge_base/atoms/ 下的结构化 JSON 原子 → 拼装文本与元数据 → 向量化 → 存入内存向量数据库
 */
@Service
@Slf4j
public class DataLoadingService {

    @Autowired
    private EmbeddingModel embeddingModel;

    @Autowired
    private EmbeddingStore<TextSegment> embeddingStore;

    @Autowired
    private ResourceLoader resourceLoader;

    @PostConstruct
    public void init() {
        log.info("🚀 开始进行基于结构化知识原子(Knowledge Atoms)的知识库初始化...");

        try {
            Resource resourcePath = resourceLoader.getResource("classpath:knowledge_base/atoms");
            File atomsDir = resourcePath.getFile();

            if (!atomsDir.exists() || !atomsDir.isDirectory()) {
                log.warn("⚠️ 知识原子目录不存在: {}", atomsDir.getAbsolutePath());
                return;
            }

            List<Document> documents = new ArrayList<>();

            try (Stream<Path> paths = Files.walk(Paths.get(atomsDir.toURI()))) {
                paths.filter(Files::isRegularFile)
                     .filter(p -> p.toString().endsWith(".json"))
                     .forEach(path -> {
                         try {
                             String content = Files.readString(path);
                             JSONObject atom = JSON.parseObject(content);
                             
                             String subject = atom.getString("subject");
                             log.info("🔍 正在解析原子: {}", subject);
                             
                             String id = atom.getString("id");
                             String category = atom.getString("category");
                             String difficulty = atom.getString("difficulty");
                             
                             JSONObject contentObj = atom.getJSONObject("content");
                             if (contentObj == null) {
                                 log.warn("⚠️ 原子 [{}] 内容区(content)为空，跳过", subject);
                                 return;
                             }
                             
                             String principles = contentObj.getString("principles");
                             
                             // 构建送入向量库的纯文本
                             StringBuilder textBuilder = new StringBuilder();
                             textBuilder.append("考核点: ").append(subject).append("\n");
                             textBuilder.append("核心原理与标准答案: ").append(principles).append("\n");
                             
                             // 【增强修复】兼容处理 pitfalls (可能是 String 或 Array)
                             Object pitfallsObj = contentObj.get("pitfalls");
                             if (pitfallsObj instanceof JSONArray) {
                                 JSONArray array = (JSONArray) pitfallsObj;
                                 if (!array.isEmpty()) {
                                     textBuilder.append("面试常见陷阱与候选人易错点:\n");
                                     for (int i = 0; i < array.size(); i++) {
                                         textBuilder.append("- ").append(array.getString(i)).append("\n");
                                     }
                                 }
                             } else if (pitfallsObj instanceof String && !((String) pitfallsObj).isEmpty()) {
                                 textBuilder.append("面试常见陷阱与候选人易错点: ").append(pitfallsObj).append("\n");
                             }
                             
                             // 【增强修复】兼容处理 follow_up_paths (可能是 String 或 Array)
                             Object followUpsObj = contentObj.get("follow_up_paths");
                             if (followUpsObj instanceof JSONArray) {
                                 JSONArray array = (JSONArray) followUpsObj;
                                 if (!array.isEmpty()) {
                                     textBuilder.append("推荐的深度追问路径:\n");
                                     for (int i = 0; i < array.size(); i++) {
                                         textBuilder.append("- ").append(array.getString(i)).append("\n");
                                     }
                                 }
                             } else if (followUpsObj instanceof String && !((String) followUpsObj).isEmpty()) {
                                 textBuilder.append("推荐的深度追问路径: ").append(followUpsObj).append("\n");
                             }
                             
                             // 提取 Metadata 元数据
                             Metadata metadata = new Metadata();
                             if (id != null) metadata.put("id", id);
                             if (category != null) metadata.put("category", category);
                             if (difficulty != null) metadata.put("difficulty", difficulty);
                             
                             Object tagsObj = atom.get("tags");
                             if (tagsObj instanceof JSONArray) {
                                 metadata.put("tags", String.join(",", ((JSONArray) tagsObj).toList(String.class)));
                             }
                             
                             documents.add(Document.from(textBuilder.toString(), metadata));
                         } catch (Exception e) {
                             log.error("❌ 解析 JSON 知识原子失败 [{}]: {}", path.getFileName(), e.getMessage());
                         }
                     });
            }

            if (documents.isEmpty()) {
                log.warn("⚠️ 未找到任何有效的 JSON 知识原子。");
                return;
            }

            EmbeddingStoreIngestor ingestor = EmbeddingStoreIngestor.builder()
                    .documentSplitter(DocumentSplitters.recursive(2000, 0))
                    .embeddingModel(embeddingModel)
                    .embeddingStore(embeddingStore)
                    .build();

            ingestor.ingest(documents);
            log.info("✅ 全量结构化知识库加载完成，共 [{}] 个核心知识原子。", documents.size());

        } catch (Exception e) {
            log.error("❌ 知识库初始化严重异常: {}", e.getMessage(), e);
        }
    }
}