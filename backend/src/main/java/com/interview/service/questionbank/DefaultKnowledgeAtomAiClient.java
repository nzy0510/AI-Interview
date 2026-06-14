package com.interview.service.questionbank;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.interview.service.UserLlmModelFactory;
import com.interview.service.UserLlmRuntimeConfig;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.output.Response;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@Service
public class DefaultKnowledgeAtomAiClient implements KnowledgeAtomAiClient {

    private static final Duration ATOM_GENERATION_TIMEOUT = Duration.ofMinutes(5);

    private final UserLlmModelFactory modelFactory;

    public DefaultKnowledgeAtomAiClient(UserLlmModelFactory modelFactory) {
        this.modelFactory = modelFactory;
    }

    @Override
    public KnowledgeAtomDraftBundle generateReviewedAtoms(UserLlmRuntimeConfig runtimeConfig, String markdown) {
        Response<AiMessage> response = modelFactory.createChatModel(runtimeConfig, ATOM_GENERATION_TIMEOUT).generate(List.of(
                new SystemMessage("""
                        你是面试题库知识原子生成器。请从 Markdown 中提取最多 100 个知识原子，并对每个原子完成二审。
                        尽量在单次调用内完整覆盖 Markdown 中适合面试考察的知识点；只有确认可提取知识点超过 100 个时，才将 atomLimitReached 置为 true。
                        只返回 JSON，不要 Markdown 代码块。结构：
                        {"atomLimitReached":false,"atoms":[{"subject":"","category":"","difficulty":"MEDIUM","tags":[],"principles":"","pitfalls":"","followUpPaths":[],"review":{"status":"PASS","reason":"","confidence":0.8,"suggestedPatch":null}}]}
                        review.status 只能是 PASS、NEEDS_REVIEW、REJECT。
                        """),
                new UserMessage(markdown)
        ));
        return parse(response.content().text());
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
