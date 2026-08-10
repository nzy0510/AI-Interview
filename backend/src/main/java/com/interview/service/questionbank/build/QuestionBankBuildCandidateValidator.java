package com.interview.service.questionbank.build;

import com.alibaba.fastjson2.JSON;
import com.interview.entity.QuestionBankBuildCandidate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
public class QuestionBankBuildCandidateValidator {
    private static final Set<String> DIFFICULTIES = Set.of("junior", "mid", "senior", "principal");

    public void validate(QuestionBankBuildCandidate candidate) {
        if (candidate == null || blank(candidate.getSubject()) || blank(candidate.getCategory())
                || blank(candidate.getDifficulty()) || blank(candidate.getPrinciples())) {
            throw new IllegalArgumentException("候选原子的主题、分类、难度和原则不能为空");
        }
        String difficulty = candidate.getDifficulty().trim().toLowerCase(Locale.ROOT);
        if (!DIFFICULTIES.contains(difficulty)) {
            throw new IllegalArgumentException("difficulty 只能是 junior、mid、senior 或 principal");
        }
        candidate.setDifficulty(difficulty);
        List<String> followUps = stringList(candidate.getFollowUpPathsJson());
        if (followUps.size() < 2 || followUps.stream().anyMatch(this::blank)) {
            throw new IllegalArgumentException("至少需要两条非空追问路径");
        }
        if (blank(candidate.getSourceRef()) || !hasSourceEvidence(candidate.getSourceEvidenceJson())) {
            throw new IllegalArgumentException("候选原子缺少来源证据");
        }
    }

    public void validate(QuestionBankBuildCandidate candidate, List<String> allowedCategories) {
        validate(candidate);
        if (allowedCategories == null || allowedCategories.isEmpty()) {
            throw new IllegalArgumentException("本批次尚未生成有效分类目录");
        }
        String category = candidate.getCategory().trim();
        for (String allowed : allowedCategories) {
            if (allowed != null && category.equalsIgnoreCase(allowed.trim())) {
                candidate.setCategory(allowed.trim());
                return;
            }
        }
        throw new IllegalArgumentException("候选原子分类不在本批次分类范围内: " + category);
    }

    private List<String> stringList(String json) {
        if (json == null || json.isBlank()) return List.of();
        try {
            List<String> values = JSON.parseArray(json, String.class);
            return values == null ? List.of() : values;
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("候选原子的追问路径格式无效");
        }
    }

    private boolean hasSourceEvidence(String json) {
        if (json == null || json.isBlank()) return false;
        try {
            List<Map> evidence = JSON.parseArray(json, Map.class);
            return evidence != null && evidence.stream().anyMatch(item -> item != null
                    && item.get("quote") != null && !blank(String.valueOf(item.get("quote"))));
        } catch (RuntimeException e) {
            return false;
        }
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
