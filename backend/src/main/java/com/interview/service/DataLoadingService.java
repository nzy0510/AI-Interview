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
        log.info("🚀 开始进行岗位隔离的知识库初始化...");

        try {
            Resource resourcePath = resourceLoader.getResource("classpath:knowledge_base");
            File knowledgeBaseDir = resourcePath.getFile();

            if (!knowledgeBaseDir.exists() || !knowledgeBaseDir.isDirectory()) {
                log.warn("⚠️ 知识库根目录不存在: {}", knowledgeBaseDir.getAbsolutePath());
                return;
            }

            // 遍历子目录：java, frontend, common
            File[] subDirs = knowledgeBaseDir.listFiles(File::isDirectory);
            if (subDirs == null || subDirs.length == 0) {
                log.warn("⚠️ 知识库下无子目录，请检查结构。");
                return;
            }

            for (File subDir : subDirs) {
                String category = subDir.getName(); // 文件夹名即为分类标签
                log.info("📂 正在加载 [{}] 分类下的文档...", category);

                List<Document> documents = FileSystemDocumentLoader.loadDocuments(
                        subDir.toPath(),
                        new ApacheTikaDocumentParser()
                );

                if (documents.isEmpty()) continue;

                // 为该分类下的所有文本片段打上 metadata 标签
                EmbeddingStoreIngestor ingestor = EmbeddingStoreIngestor.builder()
                        .documentSplitter(DocumentSplitters.recursive(500, 50))
                        .textSegmentTransformer(textSegment -> {
                            textSegment.metadata().put("category", category);
                            return textSegment;
                        })
                        .embeddingModel(embeddingModel)
                        .embeddingStore(embeddingStore)
                        .build();

                ingestor.ingest(documents);
                log.info("✅ [{}] 分类加载完成，共 {} 份文档。", category, documents.size());
            }

            log.info("🎉 全量知识库流水线构建完毕，由于采用了 Metadata 隔离，不同岗位将不再‘串台’。");

        } catch (Exception e) {
            log.error("❌ 岗位隔离初始化异常: {}", e.getMessage(), e);
        }
    }
}
