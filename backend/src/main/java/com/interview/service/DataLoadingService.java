package com.interview.service;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.loader.FileSystemDocumentLoader;
import dev.langchain4j.data.document.parser.apache.tika.ApacheTikaDocumentParser;
import dev.langchain4j.data.document.splitter.DocumentSplitters;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.EmbeddingStoreIngestor;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;

import java.io.File;
import java.util.List;

/**
 * RAG 知识库加载服务：Spring Boot 启动时自动执行
 * 职责：读取 resources/knowledge_base/ 下的所有文档 → 切块 → 向量化 → 存入内存向量数据库
 */
@Service
@Slf4j
public class DataLoadingService {

    @Autowired
    private EmbeddingModel embeddingModel; // 本地嵌入模型，将文档片段转化为多维向量

    @Autowired
    private EmbeddingStore<TextSegment> embeddingStore; // 内存级向量数据库，存储向量化后的文档

    @Autowired
    private ResourceLoader resourceLoader; // Spring 资源加载器，用于读取 classpath 下的文件

    /**
     * @PostConstruct 注解：Spring 完成依赖注入后自动调用此方法
     * 整个 RAG 知识库的加载流程：
     * 1. 定位 knowledge_base 目录
     * 2. 读取目录下所有文档（MD/TXT/PDF 等）
     * 3. 将文档切分为 500 字符的小块（每块重叠 50 字符确保上下文连贯）
     * 4. 将每个小块通过 AllMiniLmL6V2 模型算出向量
     * 5. 将向量存入 InMemoryEmbeddingStore，供后续检索使用
     */
    @PostConstruct
    public void init() {
        log.info("🚀 开始初始化面试题库与 RAG 向量检索...");

        try {
            // 定位 classpath 下的知识库目录
            Resource resourcePath = resourceLoader.getResource("classpath:knowledge_base");
            File knowledgeBaseDir = resourcePath.getFile();

            if (!knowledgeBaseDir.exists() || !knowledgeBaseDir.isDirectory()) {
                log.warn("⚠️ 知识库目录不存在: {}", knowledgeBaseDir.getAbsolutePath());
                return;
            }

            // 使用 Apache Tika 解析器加载目录下所有文档（支持 MD/TXT/PDF 等多种格式）
            List<Document> documents = FileSystemDocumentLoader.loadDocuments(
                    knowledgeBaseDir.toPath(),
                    new ApacheTikaDocumentParser()
            );     

            log.info("📚 找到 {} 份知识库文档，准备进行向量化切分...", documents.size());

            // 构建文档摄取器：切块(500字符/块, 50字符重叠) → 向量化 → 存入内存库
            EmbeddingStoreIngestor ingestor = EmbeddingStoreIngestor.builder()
                    .documentSplitter(DocumentSplitters.recursive(500, 50)) // 递归切分策略
                    .embeddingModel(embeddingModel)   // 本地 AllMiniLmL6V2 模型计算向量
                    .embeddingStore(embeddingStore)   // 存入 InMemoryEmbeddingStore
                    .build();

            ingestor.ingest(documents); // 执行摄取！

            log.info("✅ 知识库加载并向量化完成！系统已具备字节跳动底层原理考查能力。");

        } catch (Exception e) {
            log.error("❌ 知识库加载异常: {}", e.getMessage(), e);
        }
    }
}
