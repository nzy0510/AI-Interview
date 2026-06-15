package com.interview.service.questionbank;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.interview.dto.questionbank.KnowledgeAtomPayload;
import com.interview.dto.questionbank.QuestionBankImportRequest;
import com.interview.entity.InterviewPosition;
import com.interview.entity.KnowledgeAtom;
import com.interview.entity.KnowledgeBase;
import com.interview.mapper.InterviewPositionMapper;
import com.interview.mapper.KnowledgeAtomMapper;
import com.interview.mapper.KnowledgeBaseMapper;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class QuestionBankBootstrapService {

    private static final String SCOPE_PUBLIC = "PUBLIC";
    private static final String STATUS_ACTIVE = "ACTIVE";
    private static final String STATUS_PUBLISHED = "PUBLISHED";
    private static final List<String> WEB_PUBLIC_CATEGORIES = List.of(
            "React", "Vue", "Flutter", "HTML", "CSS", "JavaScript", "NodeJS", "Webpack",
            "浏览器", "前端工程化", "TypeScript", "性能优化"
    );
    private static final List<String> AI_PUBLIC_CATEGORIES = List.of("AI大模型", "AI 大模型", "大模型", "LLM", "RAG");
    private static final List<String> NON_JAVA_PUBLIC_CATEGORIES = java.util.stream.Stream
            .concat(WEB_PUBLIC_CATEGORIES.stream(), AI_PUBLIC_CATEGORIES.stream())
            .toList();

    private final KnowledgeAtomMapper atomMapper;
    private final QuestionBankService questionBankService;
    private final InterviewPositionMapper positionMapper;
    private final KnowledgeBaseMapper knowledgeBaseMapper;

    @Value("${question-bank.bootstrap.seed-from-json:true}")
    private boolean seedFromJson;

    @Value("${question-bank.bootstrap.reindex-unsynced-on-startup:true}")
    private boolean reindexUnsyncedOnStartup;

    public QuestionBankBootstrapService(KnowledgeAtomMapper atomMapper,
                                        QuestionBankService questionBankService,
                                        InterviewPositionMapper positionMapper,
                                        KnowledgeBaseMapper knowledgeBaseMapper) {
        this.atomMapper = atomMapper;
        this.questionBankService = questionBankService;
        this.positionMapper = positionMapper;
        this.knowledgeBaseMapper = knowledgeBaseMapper;
    }

    @PostConstruct
    public void init() {
        try {
            Long count = atomMapper.selectCount(new QueryWrapper<>());
            if (seedFromJson && count == 0) {
                seedLegacyJsonAtoms();
            } else if (count != null && count > 0) {
                int backfilled = backfillLegacyPublicAtomScope();
                if (backfilled > 0) {
                    log.info("Question bank legacy public atom scope backfilled: {}", backfilled);
                }
            }
            if (reindexUnsyncedOnStartup) {
                int synced = questionBankService.reindexUnsyncedPublishedAtoms();
                if (synced > 0) log.info("Question bank unsynced Qdrant vectors rebuilt: {}", synced);
            }
        } catch (Exception e) {
            log.warn("Question bank bootstrap skipped: {}", e.getMessage());
        }
    }

    private void seedLegacyJsonAtoms() throws Exception {
        PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
        Resource[] resources = resolver.getResources("classpath*:knowledge_base/atoms/**/*.json");
        Map<String, KnowledgeAtomPayload> atomsById = new LinkedHashMap<>();
        for (Resource resource : resources) {
            try (InputStream inputStream = resource.getInputStream()) {
                String content = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
                JSONObject raw = JSON.parseObject(content);
                KnowledgeAtomPayload payload = new KnowledgeAtomPayload();
                payload.setId(raw.getString("id"));
                payload.setSubject(raw.getString("subject"));
                payload.setCategory(raw.getString("category"));
                payload.setDifficulty(raw.getString("difficulty"));
                payload.setTags(readStringArray(raw.get("tags")));
                payload.setSourceRef(resource.getFilename());

                JSONObject contentObj = raw.getJSONObject("content");
                KnowledgeAtomPayload.Content atomContent = new KnowledgeAtomPayload.Content();
                if (contentObj != null) {
                    atomContent.setPrinciples(contentObj.getString("principles"));
                    atomContent.setPitfalls(readFlexibleText(contentObj.get("pitfalls")));
                    atomContent.setFollowUpPaths(readStringArray(contentObj.get("follow_up_paths")));
                }
                payload.setContent(atomContent);
                if (payload.getId() != null && payload.getSubject() != null && atomContent.getPrinciples() != null) {
                    KnowledgeAtomPayload previous = atomsById.putIfAbsent(payload.getId(), payload);
                    if (previous != null) {
                        log.warn("Legacy atom seed duplicate id skipped: {} from {}", payload.getId(), resource.getFilename());
                    }
                }
            } catch (Exception e) {
                log.warn("Legacy atom seed skipped for {}: {}", resource.getFilename(), e.getMessage());
            }
        }
        Map<String, List<KnowledgeAtomPayload>> atomsByPosition = new LinkedHashMap<>();
        for (KnowledgeAtomPayload payload : atomsById.values()) {
            atomsByPosition.computeIfAbsent(publicPositionNameFor(payload.getCategory()), ignored -> new ArrayList<>())
                    .add(payload);
        }
        if (atomsByPosition.isEmpty()) {
            log.warn("No legacy JSON atoms found for question bank bootstrap");
            return;
        }
        int seeded = 0;
        for (Map.Entry<String, List<KnowledgeAtomPayload>> entry : atomsByPosition.entrySet()) {
            ScopedPublicTarget target = loadPublicTarget(entry.getKey());
            if (target == null) {
                log.warn("Legacy atom seed skipped for missing public position: {}", entry.getKey());
                continue;
            }
            QuestionBankImportRequest request = new QuestionBankImportRequest();
            request.setBatchId("seed-legacy-json-atoms-" + target.positionId() + "-" + System.currentTimeMillis());
            request.setSourceRef("classpath:knowledge_base/atoms");
            request.setMode("AUTO_PUBLISH");
            request.setAtoms(entry.getValue());
            questionBankService.importBatch(request,
                    new QuestionBankImportScope("PUBLIC", null, target.positionId(), target.knowledgeBaseId(), null, true));
            seeded += entry.getValue().size();
        }
        log.info("Question bank seeded from legacy JSON atoms: {}", seeded);
    }

    private int backfillLegacyPublicAtomScope() {
        int updated = 0;
        updated += backfillLegacyPublicAtoms(loadPublicTarget("Web 前端开发"), WEB_PUBLIC_CATEGORIES, false);
        updated += backfillLegacyPublicAtoms(loadPublicTarget("AI 大模型应用开发"), AI_PUBLIC_CATEGORIES, false);
        updated += backfillLegacyPublicAtoms(loadPublicTarget("Java 后端开发"), NON_JAVA_PUBLIC_CATEGORIES, true);
        return updated;
    }

    private int backfillLegacyPublicAtoms(ScopedPublicTarget target, List<String> categories, boolean defaultTarget) {
        if (target == null) {
            return 0;
        }
        UpdateWrapper<KnowledgeAtom> wrapper = new UpdateWrapper<KnowledgeAtom>()
                .set("scope", SCOPE_PUBLIC)
                .set("owner_user_id", null)
                .set("position_id", target.positionId())
                .set("knowledge_base_id", target.knowledgeBaseId())
                .set("publication_status", STATUS_PUBLISHED)
                .set("review_status", "PASS")
                .set("vector_status", "PENDING")
                .setSql("published_at = COALESCE(published_at, last_indexed_at, update_time, create_time)")
                .eq("scope", SCOPE_PUBLIC)
                .eq("status", STATUS_PUBLISHED)
                .and(scope -> scope.isNull("position_id")
                        .or().isNull("knowledge_base_id")
                        .or().isNull("publication_status")
                        .or().ne("publication_status", STATUS_PUBLISHED));
        if (defaultTarget) {
            wrapper.and(category -> category.isNull("category").or().notIn("category", categories));
        } else {
            wrapper.in("category", categories);
        }
        return atomMapper.update(null, wrapper);
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

    private String publicPositionNameFor(String category) {
        if (category != null) {
            String normalized = category.trim();
            if (WEB_PUBLIC_CATEGORIES.contains(normalized)) {
                return "Web 前端开发";
            }
            if (AI_PUBLIC_CATEGORIES.contains(normalized)) {
                return "AI 大模型应用开发";
            }
        }
        return "Java 后端开发";
    }

    private record ScopedPublicTarget(Long positionId, Long knowledgeBaseId) {
    }

    private List<String> readStringArray(Object value) {
        if (value instanceof JSONArray arr) {
            return arr.toList(String.class);
        }
        if (value instanceof String s && !s.isBlank()) {
            return List.of(s);
        }
        return List.of();
    }

    private String readFlexibleText(Object value) {
        if (value instanceof JSONArray arr) {
            return String.join("\n", arr.toList(String.class));
        }
        return value != null ? String.valueOf(value) : null;
    }
}
