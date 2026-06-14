package com.interview.service.questionbank;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.interview.service.UserLlmModelFactory;
import com.interview.service.UserLlmRuntimeConfig;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.output.Response;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@Service
public class DefaultKnowledgeAtomAiClient implements KnowledgeAtomAiClient {

    private static final Duration ATOM_GENERATION_TIMEOUT = Duration.ofMinutes(5);
    private static final int MAX_ATOMS_PER_GENERATION = 100;
    private static final int MARKDOWN_CHUNK_SIZE = 12_000;

    private final UserLlmModelFactory modelFactory;

    public DefaultKnowledgeAtomAiClient(UserLlmModelFactory modelFactory) {
        this.modelFactory = modelFactory;
    }

    @Override
    public KnowledgeAtomDraftBundle generateReviewedAtoms(UserLlmRuntimeConfig runtimeConfig, String markdown) {
        OpenAiChatModel model = modelFactory.createChatModel(runtimeConfig, ATOM_GENERATION_TIMEOUT);
        List<KnowledgeAtomDraft> atoms = new ArrayList<>();
        boolean atomLimitReached = false;
        List<String> chunks = chunkMarkdown(markdown);
        for (int i = 0; i < chunks.size(); i++) {
            int remaining = MAX_ATOMS_PER_GENERATION - atoms.size();
            if (remaining <= 0) {
                atomLimitReached = true;
                break;
            }
            Response<AiMessage> response = model.generate(List.of(
                    new SystemMessage(systemPrompt(remaining, i + 1, chunks.size())),
                    new UserMessage(chunks.get(i))
            ));
            KnowledgeAtomDraftBundle bundle = parse(response.content().text());
            List<KnowledgeAtomDraft> generated = bundle.atoms() == null ? List.of() : bundle.atoms();
            atoms.addAll(generated.stream().limit(remaining).toList());
            if (bundle.atomLimitReached() || generated.size() > remaining) {
                atomLimitReached = true;
            }
        }
        return new KnowledgeAtomDraftBundle(atoms, atomLimitReached);
    }

    private String systemPrompt(int remainingAtoms, int chunkNo, int chunkCount) {
        return """
                你是面试题库知识原子生成器。当前 Markdown 已按标题、段落、列表和代码块边界分块，这是第 %d/%d 块。
                请只基于当前分块提取最多 %d 个独立知识原子，并对每个原子完成二审。
                跳过目录、页眉页脚、重复内容和低价值背景描述；如果当前分块没有可用于面试考察的知识点，返回空 atoms。
                只返回 JSON，不要 Markdown 代码块。结构：
                {"atomLimitReached":false,"atoms":[{"subject":"","category":"","difficulty":"MEDIUM","tags":[],"principles":"","pitfalls":"","followUpPaths":[],"review":{"status":"PASS","reason":"","confidence":0.8,"suggestedPatch":null}}]}
                review.status 只能是 PASS、NEEDS_REVIEW、REJECT。
                """.formatted(chunkNo, chunkCount, remainingAtoms);
    }

    private List<String> chunkMarkdown(String markdown) {
        String value = markdown == null ? "" : markdown.trim();
        if (value.isEmpty()) {
            return List.of("");
        }
        List<String> chunks = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String section : splitMarkdownSections(value)) {
            for (String piece : splitOversizedSection(section)) {
                if (current.isEmpty()) {
                    current.append(piece);
                } else if (current.length() + 2 + piece.length() <= MARKDOWN_CHUNK_SIZE) {
                    current.append("\n\n").append(piece);
                } else {
                    chunks.add(current.toString().trim());
                    current.setLength(0);
                    current.append(piece);
                }
            }
        }
        if (!current.isEmpty()) {
            chunks.add(current.toString().trim());
        }
        return chunks.isEmpty() ? List.of(value) : chunks;
    }

    private List<String> splitMarkdownSections(String markdown) {
        List<String> sections = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String line : markdown.split("\\R", -1)) {
            if (isHeading(line) && !current.isEmpty()) {
                sections.add(current.toString().trim());
                current.setLength(0);
            }
            current.append(line).append('\n');
        }
        if (!current.isEmpty()) {
            sections.add(current.toString().trim());
        }
        return sections;
    }

    private List<String> splitOversizedSection(String section) {
        String value = section.trim();
        if (value.length() <= MARKDOWN_CHUNK_SIZE) {
            return List.of(value);
        }
        String heading = "";
        String body = value;
        int firstLineEnd = value.indexOf('\n');
        if (firstLineEnd > 0 && isHeading(value.substring(0, firstLineEnd))) {
            heading = value.substring(0, firstLineEnd).trim();
            body = value.substring(firstLineEnd + 1).trim();
        }
        return packUnits(splitMarkdownUnits(body), heading);
    }

    private List<String> splitMarkdownUnits(String markdown) {
        List<String> units = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inCodeFence = false;
        for (String line : markdown.split("\\R", -1)) {
            if (line.stripLeading().startsWith("```")) {
                current.append(line).append('\n');
                inCodeFence = !inCodeFence;
                if (!inCodeFence) {
                    addUnit(units, current);
                }
                continue;
            }
            if (inCodeFence) {
                current.append(line).append('\n');
                continue;
            }
            if (line.isBlank()) {
                addUnit(units, current);
                continue;
            }
            if (isHeading(line) || isListLike(line)) {
                addUnit(units, current);
                current.append(line).append('\n');
                addUnit(units, current);
                continue;
            }
            current.append(line).append('\n');
        }
        addUnit(units, current);
        return units;
    }

    private List<String> packUnits(List<String> units, String heading) {
        List<String> chunks = new ArrayList<>();
        String prefix = heading == null || heading.isBlank() ? "" : heading.trim() + "\n";
        int maxBodyLength = Math.max(1, MARKDOWN_CHUNK_SIZE - prefix.length());
        StringBuilder current = new StringBuilder(prefix);
        for (String unit : units) {
            for (String piece : splitLargeUnit(unit, maxBodyLength)) {
                int separatorLength = current.length() > prefix.length() ? 2 : 0;
                if (current.length() + separatorLength + piece.length() > MARKDOWN_CHUNK_SIZE
                        && current.length() > prefix.length()) {
                    chunks.add(current.toString().trim());
                    current.setLength(0);
                    current.append(prefix);
                    separatorLength = 0;
                }
                if (separatorLength > 0) {
                    current.append("\n\n");
                }
                current.append(piece);
            }
        }
        if (current.length() > prefix.length()) {
            chunks.add(current.toString().trim());
        }
        return chunks;
    }

    private List<String> splitLargeUnit(String unit, int maxLength) {
        String value = unit.trim();
        if (value.length() <= maxLength) {
            return List.of(value);
        }
        List<String> pieces = new ArrayList<>();
        int cursor = 0;
        while (cursor < value.length()) {
            int end = Math.min(value.length(), cursor + maxLength);
            if (end < value.length()) {
                end = findBoundary(value, cursor, end);
            }
            pieces.add(value.substring(cursor, end).trim());
            cursor = end;
        }
        return pieces;
    }

    private int findBoundary(String value, int start, int preferredEnd) {
        for (int i = preferredEnd; i > start + MARKDOWN_CHUNK_SIZE / 3; i--) {
            char ch = value.charAt(i - 1);
            if (ch == '\n' || ch == '。' || ch == '！' || ch == '？' || ch == '；'
                    || ch == '.' || ch == '!' || ch == '?' || ch == ';') {
                return i;
            }
        }
        return preferredEnd;
    }

    private void addUnit(List<String> units, StringBuilder current) {
        if (!current.isEmpty()) {
            units.add(current.toString().trim());
            current.setLength(0);
        }
    }

    private boolean isHeading(String line) {
        return line != null && line.matches("^#{1,6}\\s+.+$");
    }

    private boolean isListLike(String line) {
        return line != null && line.matches("^\\s*(-|\\*|\\+|\\d+[.)]|>)\\s+.+$");
    }

    KnowledgeAtomDraftBundle parse(String raw) {
        JSONObject root = JSON.parseObject(stripCodeFence(raw));
        JSONArray atomArray = root.getJSONArray("atoms");
        List<KnowledgeAtomDraft> atoms = new ArrayList<>();
        if (atomArray != null) {
            for (Object item : atomArray) {
                if (!(item instanceof JSONObject atom)) continue;
                JSONObject review = atom.getJSONObject("review");
                atoms.add(new KnowledgeAtomDraft(
                        atom.getString("subject"),
                        atom.getString("category"),
                        atom.getString("difficulty"),
                        atom.getList("tags", String.class),
                        atom.getString("principles"),
                        atom.getString("pitfalls"),
                        atom.getList("followUpPaths", String.class),
                        parseReview(review)
                ));
            }
        }
        return new KnowledgeAtomDraftBundle(atoms, Boolean.TRUE.equals(root.getBoolean("atomLimitReached")));
    }

    private KnowledgeAtomReviewResult parseReview(JSONObject review) {
        if (review == null) {
            return new KnowledgeAtomReviewResult("NEEDS_REVIEW", "模型未返回二审结果", null, null);
        }
        JSONObject patch = review.getJSONObject("suggestedPatch");
        return new KnowledgeAtomReviewResult(
                review.getString("status"),
                review.getString("reason"),
                review.getDouble("confidence"),
                patch == null ? null : new KnowledgeAtomPatch(
                        patch.getString("subject"),
                        patch.getString("category"),
                        patch.getString("difficulty"),
                        patch.getList("tags", String.class),
                        patch.getString("principles"),
                        patch.getString("pitfalls"),
                        patch.getList("followUpPaths", String.class)
                )
        );
    }

    private String stripCodeFence(String raw) {
        String value = raw == null ? "" : raw.trim();
        if (value.startsWith("```")) {
            int firstLineEnd = value.indexOf('\n');
            int lastFence = value.lastIndexOf("```");
            if (firstLineEnd >= 0 && lastFence > firstLineEnd) {
                return value.substring(firstLineEnd + 1, lastFence).trim();
            }
        }
        return value;
    }
}
