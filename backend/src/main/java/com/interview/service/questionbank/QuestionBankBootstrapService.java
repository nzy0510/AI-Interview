package com.interview.service.questionbank;

import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.interview.dto.questionbank.QuestionBankImportRequest;
import com.interview.entity.InterviewPosition;
import com.interview.entity.KnowledgeBase;
import com.interview.entity.KnowledgeAtomImportBatch;
import com.interview.mapper.InterviewPositionMapper;
import com.interview.mapper.KnowledgeAtomImportBatchMapper;
import com.interview.mapper.KnowledgeBaseMapper;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.task.TaskExecutor;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class QuestionBankBootstrapService {

    private static final String SCOPE_PUBLIC = "PUBLIC";
    private static final String STATUS_ACTIVE = "ACTIVE";
    private static final int VECTOR_SYNC_MAX_ATTEMPTS = 20;
    private static final long VECTOR_SYNC_RETRY_DELAY_MS = 15_000L;

    private final QuestionBankService questionBankService;
    private final InterviewPositionMapper positionMapper;
    private final KnowledgeBaseMapper knowledgeBaseMapper;
    private final KnowledgeAtomImportBatchMapper batchMapper;
    private final TaskExecutor questionBankSyncTaskExecutor;

    @Value("${question-bank.bootstrap.seed-from-json:true}")
    private boolean seedFromJson;

    @Value("${question-bank.bootstrap.reindex-unsynced-on-startup:true}")
    private boolean reindexUnsyncedOnStartup;

    public QuestionBankBootstrapService(QuestionBankService questionBankService,
                                        InterviewPositionMapper positionMapper,
                                        KnowledgeBaseMapper knowledgeBaseMapper,
                                        KnowledgeAtomImportBatchMapper batchMapper,
                                        @Qualifier("questionBankSyncTaskExecutor") TaskExecutor questionBankSyncTaskExecutor) {
        this.questionBankService = questionBankService;
        this.positionMapper = positionMapper;
        this.knowledgeBaseMapper = knowledgeBaseMapper;
        this.batchMapper = batchMapper;
        this.questionBankSyncTaskExecutor = questionBankSyncTaskExecutor;
    }

    @PostConstruct
    public void init() {
        try {
            if (seedFromJson) {
                int imported = seedBuiltInPublicImportPackages();
                if (imported > 0) {
                    log.info("Question bank built-in public packages imported: {}", imported);
                }
                int retired = retireLegacyBuiltInAtoms();
                if (retired > 0) {
                    log.info("Question bank retired legacy built-in atoms: {}", retired);
                }
            }
            if (reindexUnsyncedOnStartup) {
                questionBankSyncTaskExecutor.execute(this::reindexUnsyncedPublishedAtomsInBackground);
            }
        } catch (Exception e) {
            log.warn("Question bank bootstrap skipped: {}", e.getMessage());
        }
    }

    private void reindexUnsyncedPublishedAtomsInBackground() {
        for (int attempt = 1; attempt <= VECTOR_SYNC_MAX_ATTEMPTS; attempt++) {
            try {
                Map<String, Integer> result = questionBankService.reindexUnsyncedPublishedAtomResult();
                int matched = result.getOrDefault("matched", 0);
                int synced = result.getOrDefault("synced", 0);
                int deleted = result.getOrDefault("deleted", 0);
                int failed = result.getOrDefault("failed", 0);
                if (matched == 0) {
                    return;
                }
                if (failed == 0) {
                    log.info("Question bank unsynced Qdrant vectors rebuilt: matched={}, synced={}, deleted={}",
                            matched, synced, deleted);
                    return;
                }
                log.warn("Question bank background vector sync attempt {}/{} incomplete: matched={}, synced={}, deleted={}, failed={}",
                        attempt, VECTOR_SYNC_MAX_ATTEMPTS, matched, synced, deleted, failed);
            } catch (Exception e) {
                log.warn("Question bank background vector sync attempt {}/{} failed: {}",
                        attempt, VECTOR_SYNC_MAX_ATTEMPTS, e.getMessage());
            }
            if (attempt < VECTOR_SYNC_MAX_ATTEMPTS && !sleepBeforeNextVectorSyncAttempt()) {
                return;
            }
        }
        log.warn("Question bank background vector sync stopped after {} attempts; remaining failed vectors can be retried by rebuilding indexes",
                VECTOR_SYNC_MAX_ATTEMPTS);
    }

    private boolean sleepBeforeNextVectorSyncAttempt() {
        try {
            Thread.sleep(VECTOR_SYNC_RETRY_DELAY_MS);
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Question bank background vector sync interrupted");
            return false;
        }
    }

    private int seedBuiltInPublicImportPackages() throws Exception {
        PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
        Resource[] resources = resolver.getResources("classpath*:knowledge_base/imports/public/**/*.json");
        int imported = 0;
        for (Resource resource : resources) {
            try (InputStream inputStream = resource.getInputStream()) {
                String content = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
                QuestionBankImportRequest request = JSON.parseObject(content, QuestionBankImportRequest.class);
                String batchId = request.getBatchId();
                if (batchId == null || batchId.isBlank()) {
                    log.warn("Built-in question-bank package skipped without batchId: {}", resource.getFilename());
                    continue;
                }
                if (batchImportedOrReset(batchId)) {
                    continue;
                }
                ScopedPublicTarget target = loadPublicTarget(publicPositionNameFor(resource));
                if (target == null) {
                    log.warn("Built-in question-bank package skipped for missing public target: {}", resource.getFilename());
                    continue;
                }
                request.setMode("AUTO_PUBLISH");
                request.setTargetCategory(publicPositionNameFor(resource));
                questionBankService.importBatch(request,
                        new QuestionBankImportScope("PUBLIC", null, target.positionId(), target.knowledgeBaseId(), null, true, false));
                imported++;
            } catch (Exception e) {
                log.warn("Built-in question-bank package skipped for {}: {}", resource.getFilename(), e.getMessage());
            }
        }
        return imported;
    }

    private int retireLegacyBuiltInAtoms() throws Exception {
        PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
        Resource resource = resolver.getResource("classpath:knowledge_base/imports/retired-built-in-atom-ids.txt");
        if (!resource.exists()) {
            return 0;
        }
        try (InputStream inputStream = resource.getInputStream()) {
            String content = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
            List<String> atomIds = content.lines()
                    .map(String::trim)
                    .filter(line -> !line.isBlank())
                    .filter(line -> !line.startsWith("#"))
                    .distinct()
                    .toList();
            if (atomIds.isEmpty()) {
                return 0;
            }
            return questionBankService.archiveAtoms(atomIds).getOrDefault("archived", 0);
        }
    }

    private ScopedPublicTarget loadPublicTarget(String positionName) {
        InterviewPosition position = positionMapper.selectOne(new QueryWrapper<InterviewPosition>()
                .eq("scope", SCOPE_PUBLIC)
                .eq("name", positionName)
                .eq("status", STATUS_ACTIVE)
                .last("LIMIT 1"));
        if (position == null) {
            return null;
        }
        Long knowledgeBaseId = position.getDefaultKnowledgeBaseId();
        if (knowledgeBaseId == null) {
            KnowledgeBase knowledgeBase = knowledgeBaseMapper.selectOne(new QueryWrapper<KnowledgeBase>()
                    .eq("scope", SCOPE_PUBLIC)
                    .eq("position_id", position.getId())
                    .eq("status", STATUS_ACTIVE)
                    .last("LIMIT 1"));
            knowledgeBaseId = knowledgeBase == null ? null : knowledgeBase.getId();
        }
        return knowledgeBaseId == null ? null : new ScopedPublicTarget(position.getId(), knowledgeBaseId);
    }

    private boolean batchImportedOrReset(String batchId) {
        KnowledgeAtomImportBatch batch = batchMapper.selectOne(new QueryWrapper<KnowledgeAtomImportBatch>()
                .eq("batch_id", batchId)
                .last("LIMIT 1"));
        if (batch == null) {
            return false;
        }
        if ("IMPORTED".equalsIgnoreCase(batch.getStatus())) {
            return true;
        }
        log.warn("Retrying incomplete built-in question-bank package: batchId={}, status={}",
                batchId, batch.getStatus());
        batchMapper.delete(new QueryWrapper<KnowledgeAtomImportBatch>().eq("batch_id", batchId));
        return false;
    }

    private String publicPositionNameFor(Resource resource) throws Exception {
        String url = resource.getURL().toString();
        if (url.contains("/frontend/") || url.contains("\\frontend\\")) {
            return "Web 前端开发";
        }
        if (url.contains("/ai-model/") || url.contains("\\ai-model\\")) {
            return "AI 大模型应用开发";
        }
        if (url.contains("/java-backend/") || url.contains("\\java-backend\\")) {
            return "Java 后端开发";
        }
        log.warn("Unknown built-in package target, defaulting to Java backend: {}", url);
        return "Java 后端开发";
    }

    private record ScopedPublicTarget(Long positionId, Long knowledgeBaseId) {
    }
}
