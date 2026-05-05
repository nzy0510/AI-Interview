package com.interview.service.questionbank;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.interview.dto.questionbank.KnowledgeAtomPayload;
import com.interview.dto.questionbank.QuestionBankImportRequest;
import com.interview.mapper.KnowledgeAtomMapper;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
public class QuestionBankBootstrapService {

    private final KnowledgeAtomMapper atomMapper;
    private final QuestionBankService questionBankService;

    @Value("${question-bank.bootstrap.seed-from-json:true}")
    private boolean seedFromJson;

    @Value("${question-bank.bootstrap.reindex-unsynced-on-startup:true}")
    private boolean reindexUnsyncedOnStartup;

    public QuestionBankBootstrapService(KnowledgeAtomMapper atomMapper, QuestionBankService questionBankService) {
        this.atomMapper = atomMapper;
        this.questionBankService = questionBankService;
    }

    @PostConstruct
    public void init() {
        try {
            Long count = atomMapper.selectCount(new QueryWrapper<>());
            if (seedFromJson && count == 0) {
                seedLegacyJsonAtoms();
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
        List<KnowledgeAtomPayload> atoms = new ArrayList<>();
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
                    atoms.add(payload);
                }
            } catch (Exception e) {
                log.warn("Legacy atom seed skipped for {}: {}", resource.getFilename(), e.getMessage());
            }
        }
        if (atoms.isEmpty()) {
            log.warn("No legacy JSON atoms found for question bank bootstrap");
            return;
        }
        QuestionBankImportRequest request = new QuestionBankImportRequest();
        request.setBatchId("seed-legacy-json-atoms");
        request.setSourceRef("classpath:knowledge_base/atoms");
        request.setMode("AUTO_PUBLISH");
        request.setAtoms(atoms);
        questionBankService.importBatch(request);
        log.info("Question bank seeded from legacy JSON atoms: {}", atoms.size());
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
