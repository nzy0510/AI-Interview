package com.interview.service.questionbank.build;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.interview.entity.KnowledgeSourceFile;
import com.interview.entity.QuestionBankBuild;
import com.interview.entity.QuestionBankBuildCandidate;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

final class QuestionBankBuildCandidateFactory {
    private QuestionBankBuildCandidateFactory() {
    }

    static QuestionBankBuildCandidate create(QuestionBankBuild build,
                                             KnowledgeSourceFile sourceFile,
                                             int globalIndex,
                                             int localIndex,
                                             JSONObject atom,
                                             int ordinal) {
        JSONObject content = atom.getJSONObject("content");
        String subject = firstNonBlank(atom.getString("subject"), atom.getString("question"));
        String category = atom.getString("category");
        String difficulty = firstNonBlank(atom.getString("difficulty"), "mid");
        String principles = firstNonBlank(content == null ? null : content.getString("principles"), atom.getString("principles"));
        String pitfalls = firstNonBlank(content == null ? null : content.getString("pitfalls"), atom.getString("pitfalls"));
        List<String> followUps = parseStringList(content == null ? null : content.get("followUpPaths"));
        if (followUps.isEmpty()) followUps = parseStringList(content == null ? null : content.get("follow_up_paths"));
        if (followUps.isEmpty()) followUps = parseStringList(atom.get("followUpPaths"));
        if (followUps.isEmpty()) followUps = parseStringList(atom.get("follow_up_paths"));
        List<String> tags = parseStringList(atom.get("tags"));
        if (blank(subject) || blank(category) || blank(principles) || followUps.size() < 2) {
            throw new IllegalStateException("候选原子的主题、分类、难度和原则不能为空，且至少需要两条追问路径");
        }
        Object evidenceValue = atom.get("sourceEvidence");
        if (evidenceValue == null) evidenceValue = atom.get("source_evidence");
        JSONArray evidence = normalizeSourceEvidence(evidenceValue, localIndex);
        QuestionBankBuildCandidate candidate = new QuestionBankBuildCandidate();
        candidate.setBuildId(build.getId()); candidate.setOwnerUserId(build.getOwnerUserId()); candidate.setPositionId(build.getPositionId()); candidate.setKnowledgeBaseId(build.getKnowledgeBaseId());
        candidate.setSourceFileId(sourceFile.getId()); candidate.setChunkIndex(globalIndex);
        candidate.setStableAtomId(stableAtomId(sourceFile.getId(), sourceFile.getFileHash(), localIndex, ordinal));
        candidate.setSourceRef(sourceFile.getOriginalFilename() + "#chunk-" + localIndex);
        candidate.setSubject(subject.trim()); candidate.setCategory(category.trim()); candidate.setDifficulty(difficulty.trim()); candidate.setTagsJson(JSON.toJSONString(tags));
        candidate.setPrinciples(principles.trim()); candidate.setPitfalls(pitfalls == null ? "" : pitfalls.trim()); candidate.setFollowUpPathsJson(JSON.toJSONString(followUps));
        candidate.setSourceEvidenceJson(evidence.toJSONString()); candidate.setReviewStatus("PENDING");
        return candidate;
    }

    static String stableAtomId(Long sourceFileId, String fileHash, int localIndex, int ordinal) {
        String raw = sourceFileId + "|" + String.valueOf(fileHash) + "|" + localIndex + "|" + ordinal;
        try {
            return "generated-" + HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(raw.getBytes(StandardCharsets.UTF_8))).substring(0, 28);
        } catch (Exception e) {
            throw new IllegalStateException("稳定原子 ID 生成失败", e);
        }
    }

    private static List<String> parseStringList(Object raw) {
        if (raw instanceof JSONArray array) {
            return array.stream().map(String::valueOf).map(String::trim).filter(value -> !value.isBlank()).toList();
        }
        if (raw instanceof List<?> list) {
            return list.stream().map(String::valueOf).map(String::trim).filter(value -> !value.isBlank()).toList();
        }
        if (raw instanceof String text && !text.isBlank()) {
            String trimmed = text.trim();
            try {
                if (trimmed.startsWith("[")) return JSON.parseArray(trimmed, String.class).stream().map(String::trim).filter(value -> !value.isBlank()).toList();
            } catch (Exception ignored) {
            }
            return List.of(trimmed.split("\\R")).stream().map(String::trim).filter(value -> !value.isBlank()).toList();
        }
        return List.of();
    }

    private static JSONArray normalizeSourceEvidence(Object raw, int localIndex) {
        JSONArray values = new JSONArray();
        if (raw instanceof JSONArray array) values.addAll(array);
        else if (raw instanceof Map<?, ?>) values.add(raw);
        else if (raw instanceof String text && !text.isBlank()) {
            String trimmed = text.trim();
            try {
                if (trimmed.startsWith("[")) values.addAll(JSON.parseArray(trimmed));
                else if (trimmed.startsWith("{")) values.add(JSON.parseObject(trimmed));
                else values.add(trimmed);
            } catch (Exception ignored) {
                values.add(trimmed);
            }
        }
        JSONArray result = new JSONArray();
        for (Object value : values) {
            String quote;
            String pageOrSection = null;
            if (value instanceof Map<?, ?> map) {
                Object quoteValue = map.get("quote");
                quote = quoteValue == null ? null : String.valueOf(quoteValue);
                Object pageValue = map.get("pageOrSection");
                if (pageValue == null) pageValue = map.get("page_or_section");
                pageOrSection = pageValue == null ? null : String.valueOf(pageValue);
            } else quote = value == null ? null : String.valueOf(value);
            if (blank(quote)) continue;
            JSONObject item = new JSONObject();
            item.put("quote", truncate(quote.trim(), 500));
            item.put("pageOrSection", blank(pageOrSection) ? "chunk-" + localIndex : pageOrSection.trim());
            result.add(item);
        }
        return result;
    }

    private static String firstNonBlank(String first, String fallback) { return blank(first) ? fallback : first; }
    private static boolean blank(String value) { return value == null || value.isBlank(); }
    private static String truncate(String value, int max) { return value == null || value.length() <= max ? value : value.substring(0, max); }
}
